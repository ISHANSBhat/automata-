package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.ThompsonStep;
import automata.engine.StepLogger.ThompsonResult;
import automata.util.JsonUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * State Elimination algorithm: converts a DFA/NFA to a Regular Expression.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Build a GNFA (Generalized NFA) where each transition label is a regex string</li>
 *   <li>Add a new super-start state and super-accept state</li>
 *   <li>Iteratively eliminate interior states, updating transition regexes</li>
 *   <li>The final regex is the label on the single remaining edge from super-start to super-accept</li>
 * </ol>
 */
public final class StateElimination {

    private StateElimination() {}

    // Sentinel values for regex algebra
    private static final String EMPTY_SET = "∅";  // no path
    private static final String EPSILON   = "ε";  // empty string

    /**
     * Result of DFA → Regex conversion.
     */
    public record RegexResult(String regex, List<ThompsonStep> steps) {
        public String toJson() {
            List<String> stepJsons = steps.stream()
                    .map(ThompsonStep::toJson)
                    .toList();
            return JsonUtil.objectOf(
                    "regex", JsonUtil.quoted(regex),
                    "steps", JsonUtil.array(stepJsons)
            );
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Convert an automaton (DFA or NFA) to a regular expression via state elimination.
     */
    public static RegexResult toRegex(Automaton automaton) {
        List<ThompsonStep> steps = new ArrayList<>();
        int stepNum = 1;

        if (automaton.getStates().isEmpty()) {
            return new RegexResult(EMPTY_SET, List.of(
                    new ThompsonStep(1, "result", EMPTY_SET, "Empty automaton → ∅")));
        }

        if (automaton.getStartStateId() == null) {
            return new RegexResult(EMPTY_SET, List.of(
                    new ThompsonStep(1, "result", EMPTY_SET, "No start state → ∅")));
        }

        if (automaton.getFinalStateIds().isEmpty()) {
            return new RegexResult(EMPTY_SET, List.of(
                    new ThompsonStep(1, "result", EMPTY_SET, "No final states → ∅")));
        }

        // 1. Build GNFA transition table: gnfa[src][dst] = regex
        //    All states from the original automaton + a super-start and super-accept
        String superStart  = "__S__";
        String superAccept = "__A__";

        List<String> allStates = new ArrayList<>();
        allStates.add(superStart);
        for (State s : automaton.getStates()) {
            allStates.add(s.id());
        }
        allStates.add(superAccept);

        // Initialize all transitions to EMPTY_SET
        Map<String, Map<String, String>> gnfa = new LinkedHashMap<>();
        for (String src : allStates) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String dst : allStates) {
                row.put(dst, EMPTY_SET);
            }
            gnfa.put(src, row);
        }

        // 2. Populate from original automaton
        // super-start → original start via ε
        gnfa.get(superStart).put(automaton.getStartStateId(), EPSILON);

        // original final states → super-accept via ε
        for (String fid : automaton.getFinalStateIds()) {
            gnfa.get(fid).put(superAccept, EPSILON);
        }

        // Original transitions: merge multiple symbols on the same (src, dst) pair via union
        for (Transition t : automaton.getTransitions()) {
            String src = t.sourceStateId();
            String dst = t.targetStateId();
            String sym = t.isEpsilon() ? EPSILON : t.symbol();

            String existing = gnfa.get(src).get(dst);
            gnfa.get(src).put(dst, regexUnion(existing, sym));
        }

        steps.add(new ThompsonStep(stepNum++, "init", "",
                "Built GNFA with " + allStates.size() + " states (including super-start and super-accept)"));

        // 3. Eliminate interior states (all except super-start and super-accept)
        List<String> toEliminate = new ArrayList<>();
        for (State s : automaton.getStates()) {
            toEliminate.add(s.id());
        }

        for (String qRip : toEliminate) {
            // For each pair (qi, qj) where qi != qRip, qj != qRip:
            //   R(qi, qj) = R(qi, qj) | R(qi, qRip) . R(qRip, qRip)* . R(qRip, qj)

            String selfLoop = gnfa.get(qRip).get(qRip);

            List<String> remaining = allStates.stream()
                    .filter(s -> !s.equals(qRip))
                    .toList();

            for (String qi : remaining) {
                String rToRip = gnfa.get(qi).get(qRip);
                if (rToRip.equals(EMPTY_SET)) continue;  // no path qi→qRip, skip

                for (String qj : remaining) {
                    String rFromRip = gnfa.get(qRip).get(qj);
                    if (rFromRip.equals(EMPTY_SET)) continue;  // no path qRip→qj, skip

                    String existing = gnfa.get(qi).get(qj);

                    // Build: rToRip . selfLoop* . rFromRip
                    String through = regexConcat(
                            regexConcat(rToRip, regexStar(selfLoop)),
                            rFromRip
                    );

                    String updated = regexUnion(existing, through);
                    gnfa.get(qi).put(qj, updated);
                }
            }

            // Remove qRip from allStates
            allStates.remove(qRip);
            gnfa.remove(qRip);
            for (Map<String, String> row : gnfa.values()) {
                row.remove(qRip);
            }

            // Summarize the regex so far for the step log
            String currentRegex = gnfa.get(superStart).get(superAccept);
            String stateName = automaton.getState(qRip) != null
                    ? automaton.getState(qRip).name() : qRip;

            steps.add(new ThompsonStep(stepNum++, "eliminate", stateName,
                    "Eliminate state " + stateName +
                            " → remaining: " + (allStates.size() - 2) + " interior states" +
                            " | regex so far: " + truncate(currentRegex, 120)));
        }

        // 4. Final regex is gnfa[superStart][superAccept]
        String finalRegex = gnfa.get(superStart).get(superAccept);

        steps.add(new ThompsonStep(stepNum, "result", finalRegex,
                "State elimination complete. Regex: " + finalRegex));

        return new RegexResult(finalRegex, steps);
    }

    // =========================================================================
    // Regex Algebra — simplification helpers
    // =========================================================================

    /**
     * Union: R1 | R2, with simplifications:
     * - ∅ | R = R
     * - R | ∅ = R
     * - R | R = R
     */
    static String regexUnion(String r1, String r2) {
        if (r1.equals(EMPTY_SET)) return r2;
        if (r2.equals(EMPTY_SET)) return r1;
        if (r1.equals(r2))        return r1;
        return parenthesizeUnion(r1) + "|" + parenthesizeUnion(r2);
    }

    /**
     * Concatenation: R1 . R2, with simplifications:
     * - ∅ . R = ∅
     * - R . ∅ = ∅
     * - ε . R = R
     * - R . ε = R
     */
    static String regexConcat(String r1, String r2) {
        if (r1.equals(EMPTY_SET) || r2.equals(EMPTY_SET)) return EMPTY_SET;
        if (r1.equals(EPSILON)) return r2;
        if (r2.equals(EPSILON)) return r1;
        return parenthesizeConcat(r1) + parenthesizeConcat(r2);
    }

    /**
     * Kleene star: R*, with simplifications:
     * - ∅* = ε
     * - ε* = ε
     * - (R*)* = R*
     */
    static String regexStar(String r) {
        if (r.equals(EMPTY_SET)) return EPSILON;
        if (r.equals(EPSILON))   return EPSILON;
        // Already starred?
        if (r.endsWith("*") && !needsParensForStar(r.substring(0, r.length() - 1))) {
            return r;  // (R*)* = R*
        }
        if (isSingleChar(r)) {
            return r + "*";
        }
        return "(" + r + ")*";
    }

    // =========================================================================
    // Parenthesization helpers
    // =========================================================================

    /** Returns true if the string is a single character or single escaped character. */
    private static boolean isSingleChar(String r) {
        return r.length() == 1 && !r.equals("|") && !r.equals("(") && !r.equals(")");
    }

    /** Returns true if R is "atomic" — a single char, ε, or already parenthesized. */
    private static boolean isAtomic(String r) {
        if (r.length() <= 1) return true;
        if (r.equals(EPSILON)) return true;
        if (r.equals(EMPTY_SET)) return true;
        if (r.startsWith("(") && findMatchingParen(r, 0) == r.length() - 1) return true;
        // Single char followed by * is atomic for concatenation purposes
        if (r.length() == 2 && r.charAt(1) == '*') return true;
        return false;
    }

    /** Wrap in parens for concatenation context if the expression contains union (|) at the top level. */
    private static String parenthesizeConcat(String r) {
        if (containsTopLevelUnion(r)) {
            return "(" + r + ")";
        }
        return r;
    }

    /** For union operands, no extra parens needed (union is the lowest precedence). */
    private static String parenthesizeUnion(String r) {
        return r;  // Union is lowest precedence, no wrapping needed
    }

    /** Check if we need parens before applying star. */
    private static boolean needsParensForStar(String r) {
        return r.length() > 1 && !isAtomic(r);
    }

    /** Returns true if the regex string contains a `|` at the top level (not inside parens). */
    private static boolean containsTopLevelUnion(String r) {
        int depth = 0;
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '|' && depth == 0) return true;
        }
        return false;
    }

    /** Find the index of the matching ')' for the '(' at position start. */
    private static int findMatchingParen(String r, int start) {
        int depth = 0;
        for (int i = start; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Truncate a string for display purposes. */
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
