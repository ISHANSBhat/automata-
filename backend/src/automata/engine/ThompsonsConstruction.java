package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.ThompsonStep;
import automata.engine.StepLogger.ThompsonResult;

import java.util.*;

/**
 * Thompson's Construction: converts a Regular Expression to an ε-NFA.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>Preprocess: insert explicit concatenation operators</li>
 *   <li>Shunting-Yard: convert infix to postfix</li>
 *   <li>Stack-based postfix evaluation: build NFA fragments</li>
 * </ol>
 *
 * <p>Supported operators: {@code |} (union), {@code .} (concatenation, implicit),
 * {@code *} (Kleene star), {@code +} (one-or-more), {@code ?} (zero-or-one),
 * {@code ()} (grouping).</p>
 */
public final class ThompsonsConstruction {

    private ThompsonsConstruction() {}

    private static int stateCounter;

    /** A fragment of an NFA with a single start and single accept state. */
    private record Fragment(String startId, String acceptId) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Build an ε-NFA from a regular expression string.
     *
     * @param regex the regular expression (e.g. "(a|b)*abb")
     * @return the constructed ε-NFA with step trace
     * @throws IllegalArgumentException if the regex is malformed
     */
    public static ThompsonResult build(String regex) {
        if (regex == null || regex.isEmpty()) {
            throw new IllegalArgumentException("Empty regular expression");
        }

        stateCounter = 0;
        List<ThompsonStep> steps = new ArrayList<>();

        // 1. Insert explicit concatenation
        String withConcat = insertExplicitConcat(regex);
        steps.add(new ThompsonStep(1, "preprocess", withConcat,
                "Insert explicit concatenation: " + regex + " → " + withConcat));

        // 2. Convert to postfix
        String postfix = toPostfix(withConcat);
        steps.add(new ThompsonStep(2, "postfix", postfix,
                "Convert to postfix (Shunting-Yard): " + postfix));

        // 3. Build NFA from postfix
        Automaton nfa = buildFromPostfix(postfix, steps);

        return new ThompsonResult(nfa, steps);
    }

    // =========================================================================
    // Step 1: Insert Explicit Concatenation
    // =========================================================================

    /**
     * Inserts explicit '.' concatenation operators where they are implicit.
     * E.g.: "ab" → "a.b", "a(b" → "a.(b", ")a" → ").a", "*a" → "*.a"
     */
    static String insertExplicitConcat(String regex) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            result.append(c);

            if (i + 1 < regex.length()) {
                char next = regex.charAt(i + 1);

                // Insert '.' between these pairs:
                // (literal or ) or * or + or ?) followed by (literal or ()
                if (shouldInsertConcat(c, next)) {
                    result.append('.');
                }
            }
        }

        return result.toString();
    }

    private static boolean shouldInsertConcat(char left, char right) {
        // Left side: must be something that "ends" an expression
        boolean leftEnds = isLiteral(left) || left == ')' || left == '*' || left == '+' || left == '?';
        // Right side: must be something that "starts" an expression
        boolean rightStarts = isLiteral(right) || right == '(';

        return leftEnds && rightStarts;
    }

    private static boolean isLiteral(char c) {
        return c != '(' && c != ')' && c != '|' && c != '*' && c != '+' && c != '?' && c != '.';
    }

    // =========================================================================
    // Step 2: Shunting-Yard (Infix → Postfix)
    // =========================================================================

    private static int precedence(char op) {
        return switch (op) {
            case '|' -> 1;
            case '.' -> 2;
            case '*', '+', '?' -> 3;
            default -> 0;
        };
    }

    static String toPostfix(String infix) {
        StringBuilder output = new StringBuilder();
        Deque<Character> opStack = new ArrayDeque<>();

        for (int i = 0; i < infix.length(); i++) {
            char c = infix.charAt(i);

            if (isLiteral(c) && c != '.') {
                // Operand — goes directly to output
                output.append(c);
            } else if (c == '(') {
                opStack.push(c);
            } else if (c == ')') {
                while (!opStack.isEmpty() && opStack.peek() != '(') {
                    output.append(opStack.pop());
                }
                if (opStack.isEmpty()) {
                    throw new IllegalArgumentException("Mismatched parentheses: extra ')'");
                }
                opStack.pop(); // Remove '('
            } else {
                // Operator: ., |, *, +, ?
                while (!opStack.isEmpty() && opStack.peek() != '(' &&
                        precedence(opStack.peek()) >= precedence(c)) {
                    output.append(opStack.pop());
                }
                opStack.push(c);
            }
        }

        while (!opStack.isEmpty()) {
            char op = opStack.pop();
            if (op == '(') {
                throw new IllegalArgumentException("Mismatched parentheses: extra '('");
            }
            output.append(op);
        }

        return output.toString();
    }

    // =========================================================================
    // Step 3: Postfix → NFA (Stack-based Thompson's)
    // =========================================================================

    private static Automaton buildFromPostfix(String postfix, List<ThompsonStep> steps) {
        Automaton nfa = new Automaton();
        Deque<Fragment> fragStack = new ArrayDeque<>();
        int stepNum = 3;

        for (int i = 0; i < postfix.length(); i++) {
            char c = postfix.charAt(i);

            if (c == '.') {
                // Concatenation
                if (fragStack.size() < 2) {
                    throw new IllegalArgumentException("Invalid regex: not enough operands for concatenation");
                }
                Fragment f2 = fragStack.pop();
                Fragment f1 = fragStack.pop();

                // Wire f1.accept → ε → f2.start
                nfa.addTransition(new Transition(f1.acceptId, f2.startId, Transition.EPSILON));

                // f1.accept is no longer final
                nfa.removeFinalStateId(f1.acceptId);
                updateStateFlags(nfa, f1.acceptId, false, false);

                fragStack.push(new Fragment(f1.startId, f2.acceptId));

                steps.add(new ThompsonStep(stepNum++, "concatenation", "",
                        "Concatenate: wire " + f1.acceptId + " →ε→ " + f2.startId));

            } else if (c == '|') {
                // Union
                if (fragStack.size() < 2) {
                    throw new IllegalArgumentException("Invalid regex: not enough operands for union");
                }
                Fragment f2 = fragStack.pop();
                Fragment f1 = fragStack.pop();

                String newStart = newState(nfa, true, false);
                String newAccept = newState(nfa, false, true);

                // newStart → ε → f1.start
                nfa.addTransition(new Transition(newStart, f1.startId, Transition.EPSILON));
                // newStart → ε → f2.start
                nfa.addTransition(new Transition(newStart, f2.startId, Transition.EPSILON));
                // f1.accept → ε → newAccept
                nfa.addTransition(new Transition(f1.acceptId, newAccept, Transition.EPSILON));
                // f2.accept → ε → newAccept
                nfa.addTransition(new Transition(f2.acceptId, newAccept, Transition.EPSILON));

                // Clear old start/final flags
                updateStateFlags(nfa, f1.startId, false, false);
                updateStateFlags(nfa, f2.startId, false, false);
                nfa.removeFinalStateId(f1.acceptId);
                updateStateFlags(nfa, f1.acceptId, false, false);
                nfa.removeFinalStateId(f2.acceptId);
                updateStateFlags(nfa, f2.acceptId, false, false);

                fragStack.push(new Fragment(newStart, newAccept));

                steps.add(new ThompsonStep(stepNum++, "union", "",
                        "Union: " + newStart + " →ε→ {" + f1.startId + ", " + f2.startId +
                                "}, {" + f1.acceptId + ", " + f2.acceptId + "} →ε→ " + newAccept));

            } else if (c == '*') {
                // Kleene star
                if (fragStack.isEmpty()) {
                    throw new IllegalArgumentException("Invalid regex: no operand for *");
                }
                Fragment f = fragStack.pop();

                String newStart = newState(nfa, true, false);
                String newAccept = newState(nfa, false, true);

                // newStart → ε → f.start
                nfa.addTransition(new Transition(newStart, f.startId, Transition.EPSILON));
                // newStart → ε → newAccept (zero occurrences)
                nfa.addTransition(new Transition(newStart, newAccept, Transition.EPSILON));
                // f.accept → ε → f.start (repetition)
                nfa.addTransition(new Transition(f.acceptId, f.startId, Transition.EPSILON));
                // f.accept → ε → newAccept
                nfa.addTransition(new Transition(f.acceptId, newAccept, Transition.EPSILON));

                updateStateFlags(nfa, f.startId, false, false);
                nfa.removeFinalStateId(f.acceptId);
                updateStateFlags(nfa, f.acceptId, false, false);

                fragStack.push(new Fragment(newStart, newAccept));

                steps.add(new ThompsonStep(stepNum++, "kleene_star", "",
                        "Kleene Star: " + newStart + " →ε→ " + f.startId + ", " +
                                f.acceptId + " →ε→ " + f.startId + " (loop), " +
                                newStart + " →ε→ " + newAccept + " (skip)"));

            } else if (c == '+') {
                // One or more: same as * but without newStart → newAccept shortcut
                if (fragStack.isEmpty()) {
                    throw new IllegalArgumentException("Invalid regex: no operand for +");
                }
                Fragment f = fragStack.pop();

                String newStart = newState(nfa, true, false);
                String newAccept = newState(nfa, false, true);

                nfa.addTransition(new Transition(newStart, f.startId, Transition.EPSILON));
                nfa.addTransition(new Transition(f.acceptId, f.startId, Transition.EPSILON));
                nfa.addTransition(new Transition(f.acceptId, newAccept, Transition.EPSILON));

                updateStateFlags(nfa, f.startId, false, false);
                nfa.removeFinalStateId(f.acceptId);
                updateStateFlags(nfa, f.acceptId, false, false);

                fragStack.push(new Fragment(newStart, newAccept));

                steps.add(new ThompsonStep(stepNum++, "plus", "",
                        "Plus (one-or-more): " + newStart + " →ε→ " + f.startId +
                                ", " + f.acceptId + " →ε→ " + f.startId + " (loop)"));

            } else if (c == '?') {
                // Zero or one: union with ε
                if (fragStack.isEmpty()) {
                    throw new IllegalArgumentException("Invalid regex: no operand for ?");
                }
                Fragment f = fragStack.pop();

                String newStart = newState(nfa, true, false);
                String newAccept = newState(nfa, false, true);

                nfa.addTransition(new Transition(newStart, f.startId, Transition.EPSILON));
                nfa.addTransition(new Transition(newStart, newAccept, Transition.EPSILON)); // skip
                nfa.addTransition(new Transition(f.acceptId, newAccept, Transition.EPSILON));

                updateStateFlags(nfa, f.startId, false, false);
                nfa.removeFinalStateId(f.acceptId);
                updateStateFlags(nfa, f.acceptId, false, false);

                fragStack.push(new Fragment(newStart, newAccept));

                steps.add(new ThompsonStep(stepNum++, "optional", "",
                        "Optional: " + newStart + " →ε→ {" + f.startId + ", " + newAccept + "}"));

            } else {
                // Literal character
                String s1 = newState(nfa, false, false);
                String s2 = newState(nfa, false, true);

                nfa.addTransition(new Transition(s1, s2, String.valueOf(c)));

                fragStack.push(new Fragment(s1, s2));

                steps.add(new ThompsonStep(stepNum++, "literal", String.valueOf(c),
                        "Literal '" + c + "': " + s1 + " →" + c + "→ " + s2));
            }
        }

        if (fragStack.size() != 1) {
            throw new IllegalArgumentException("Invalid regex: " + fragStack.size() +
                    " fragments remaining (expected 1)");
        }

        Fragment finalFrag = fragStack.pop();

        // Mark the final start and accept states
        updateStateFlags(nfa, finalFrag.startId, true, false);
        nfa.setStartStateId(finalFrag.startId);
        updateStateFlags(nfa, finalFrag.acceptId, false, true);
        nfa.addFinalStateId(finalFrag.acceptId);

        steps.add(new ThompsonStep(stepNum, "complete", "",
                "Construction complete. ε-NFA has " + nfa.getStates().size() + " states. " +
                        "Start: " + finalFrag.startId + ", Accept: " + finalFrag.acceptId));

        return nfa;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates a new uniquely-named state and adds it to the automaton. */
    private static String newState(Automaton nfa, boolean isStart, boolean isFinal) {
        String id = "s" + stateCounter++;
        nfa.addState(new State(id, id, isStart, isFinal));
        return id;
    }

    /** Updates the start/final flags on a state already in the automaton. */
    private static void updateStateFlags(Automaton nfa, String stateId, boolean isStart, boolean isFinal) {
        State old = nfa.getState(stateId);
        if (old != null) {
            nfa.addState(new State(stateId, old.name(), isStart, isFinal));
        }
    }
}
