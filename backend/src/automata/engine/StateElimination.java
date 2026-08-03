package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.ThompsonStep;
import automata.engine.StepLogger.ThompsonResult;
import automata.util.JsonUtil;

import java.util.*;

/**
 * State Elimination algorithm: converts a DFA/NFA to a Regular Expression.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Build a GNFA (Generalized NFA) where each transition label is a {@link RegexNode}</li>
 *   <li>Add a new super-start state and super-accept state</li>
 *   <li>Iteratively eliminate interior states, updating transition regexes</li>
 *   <li>The final regex is the label on the single remaining edge from super-start to super-accept</li>
 * </ol>
 *
 * <p>Regex algebra is performed on a tree-based AST ({@link RegexNode}) rather than
 * raw strings. Each union/concat/star operation eagerly simplifies using algebraic
 * rewrite rules (common-factor extraction, star-unfold recognition, etc.) so
 * intermediate expressions stay compact and the final result is well-simplified.</p>
 */
public final class StateElimination {

    private StateElimination() {}

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
     *
     * <p>Pipeline: convert to DFA (if needed) → minimize → GNFA → eliminate states.</p>
     */
    public static RegexResult toRegex(Automaton automaton) {
        List<ThompsonStep> steps = new ArrayList<>();
        int stepNum = 1;

        if (automaton.getStates().isEmpty()) {
            return new RegexResult("∅", List.of(
                    new ThompsonStep(1, "result", "∅", "Empty automaton → ∅")));
        }

        if (automaton.getStartStateId() == null) {
            return new RegexResult("∅", List.of(
                    new ThompsonStep(1, "result", "∅", "No start state → ∅")));
        }

        if (automaton.getFinalStateIds().isEmpty()) {
            return new RegexResult("∅", List.of(
                    new ThompsonStep(1, "result", "∅", "No final states → ∅")));
        }

        // 0a. Convert to DFA if the input is an NFA or ε-NFA
        Automaton dfa = automaton;
        if (!automaton.isDFA()) {
            boolean hasEps = automaton.hasEpsilonTransitions();
            StepLogger.ConversionResult conv = hasEps
                    ? SubsetConstruction.convertENFAtoDFA(automaton)
                    : SubsetConstruction.convertNFAtoDFA(automaton);
            dfa = conv.resultDFA();
            steps.add(new ThompsonStep(stepNum++, "convert",
                    dfa.getStates().size() + " states",
                    "Converted " + (hasEps ? "ε-NFA" : "NFA") + " to DFA (" +
                            dfa.getStates().size() + " states) via subset construction"));
        }

        // 0b. Minimize the DFA
        int beforeSize = dfa.getStates().size();
        dfa = DFAMinimization.minimize(dfa);
        int afterSize = dfa.getStates().size();
        steps.add(new ThompsonStep(stepNum++, "minimize",
                afterSize + " states",
                "Minimized DFA: " + beforeSize + " → " + afterSize + " states"));

        // 1. Build GNFA transition table: gnfa[src][dst] = RegexNode
        //    All states from the minimized DFA + a super-start and super-accept
        String superStart  = "__S__";
        String superAccept = "__A__";

        List<String> allStates = new ArrayList<>();
        allStates.add(superStart);
        for (State s : dfa.getStates()) {
            allStates.add(s.id());
        }
        allStates.add(superAccept);

        // Initialize all transitions to Empty (∅)
        Map<String, Map<String, RegexNode>> gnfa = new LinkedHashMap<>();
        for (String src : allStates) {
            Map<String, RegexNode> row = new LinkedHashMap<>();
            for (String dst : allStates) {
                row.put(dst, new RegexNode.Empty());
            }
            gnfa.put(src, row);
        }

        // 2. Populate from minimized DFA
        // super-start → original start via ε
        gnfa.get(superStart).put(dfa.getStartStateId(), new RegexNode.Eps());

        // original final states → super-accept via ε
        for (String fid : dfa.getFinalStateIds()) {
            gnfa.get(fid).put(superAccept, new RegexNode.Eps());
        }

        // Original transitions: merge multiple symbols on the same (src, dst) pair via union
        for (Transition t : dfa.getTransitions()) {
            String src = t.sourceStateId();
            String dst = t.targetStateId();
            RegexNode sym = t.isEpsilon() ? new RegexNode.Eps() : new RegexNode.Lit(t.symbol());

            RegexNode existing = gnfa.get(src).get(dst);
            gnfa.get(src).put(dst, RegexNode.union(existing, sym));
        }

        steps.add(new ThompsonStep(stepNum++, "init", "",
                "Built GNFA with " + allStates.size() + " states (including super-start and super-accept)"));

        // 3. Eliminate interior states (all except super-start and super-accept)
        //    Sort by ascending degree (incoming + outgoing edges) — low-degree-first
        //    produces shorter intermediate expressions.
        final Automaton dfaRef = dfa;
        List<String> toEliminate = new ArrayList<>();
        for (State s : dfa.getStates()) {
            toEliminate.add(s.id());
        }
        toEliminate.sort(Comparator.comparingInt(id -> stateDegree(dfaRef, id)));

        for (String qRip : toEliminate) {
            // For each pair (qi, qj) where qi != qRip, qj != qRip:
            //   R(qi, qj) = R(qi, qj) | R(qi, qRip) . R(qRip, qRip)* . R(qRip, qj)

            RegexNode selfLoop = gnfa.get(qRip).get(qRip);

            List<String> remaining = allStates.stream()
                    .filter(s -> !s.equals(qRip))
                    .toList();

            for (String qi : remaining) {
                RegexNode rToRip = gnfa.get(qi).get(qRip);
                if (rToRip instanceof RegexNode.Empty) continue;  // no path qi→qRip, skip

                for (String qj : remaining) {
                    RegexNode rFromRip = gnfa.get(qRip).get(qj);
                    if (rFromRip instanceof RegexNode.Empty) continue;  // no path qRip→qj, skip

                    RegexNode existing = gnfa.get(qi).get(qj);

                    // Build: rToRip . selfLoop* . rFromRip
                    RegexNode through = RegexNode.concat(
                            RegexNode.concat(rToRip, RegexNode.star(selfLoop)),
                            rFromRip
                    );

                    RegexNode updated = RegexNode.union(existing, through);
                    gnfa.get(qi).put(qj, updated);
                }
            }

            // Remove qRip from allStates
            allStates.remove(qRip);
            gnfa.remove(qRip);
            for (Map<String, RegexNode> row : gnfa.values()) {
                row.remove(qRip);
            }

            // Summarize the regex so far for the step log
            RegexNode currentNode = gnfa.get(superStart).get(superAccept);
            String currentRegex = RegexNode.render(currentNode);
            String stateName = dfaRef.getState(qRip) != null
                    ? dfaRef.getState(qRip).name() : qRip;

            steps.add(new ThompsonStep(stepNum++, "eliminate", stateName,
                    "Eliminate state " + stateName +
                            " → remaining: " + (allStates.size() - 2) + " interior states" +
                            " | regex so far: " + truncate(currentRegex, 120)));
        }

        // 4. Final regex is gnfa[superStart][superAccept]
        RegexNode finalNode = gnfa.get(superStart).get(superAccept);
        String finalRegex = RegexNode.render(finalNode);

        steps.add(new ThompsonStep(stepNum, "result", finalRegex,
                "State elimination complete. Regex: " + finalRegex));

        return new RegexResult(finalRegex, steps);
    }

    /**
     * Compute the degree of a state: number of incoming + outgoing transitions.
     * Used to sort elimination order — low-degree states first produce shorter regexes.
     */
    private static int stateDegree(Automaton dfa, String stateId) {
        int degree = 0;
        for (Transition t : dfa.getTransitions()) {
            if (t.sourceStateId().equals(stateId)) degree++;
            if (t.targetStateId().equals(stateId)) degree++;
        }
        return degree;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Truncate a string for display purposes. */
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
