package automata.engine;

import automata.model.Automaton;
import automata.model.Transition;
import automata.engine.StepLogger.StepDetail;
import automata.engine.StepLogger.SimulationResult;

import java.util.*;

/**
 * Pure-function simulation engine for DFA, NFA, and ε-NFA.
 * Each method takes an Automaton + input string and returns a SimulationResult
 * with accept/reject status and step-by-step trace. No side effects.
 */
public final class SimulationEngine {

    private SimulationEngine() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Simulate a string on a DFA.
     * Single deterministic state walk. O(n) in input length.
     */
    public static SimulationResult simulateDFA(Automaton a, String input) {
        List<StepDetail> steps = new ArrayList<>();
        String currentState = a.getStartStateId();

        if (currentState == null) {
            return new SimulationResult(false, List.of(
                    new StepDetail(1, "", Set.of(), "No start state defined")));
        }

        // Step 0: initial state
        steps.add(new StepDetail(1, "", Set.of(currentState),
                "Start in state " + currentState));

        for (int i = 0; i < input.length(); i++) {
            String symbol = String.valueOf(input.charAt(i));
            Set<String> targets = a.getTargetStates(currentState, symbol);

            if (targets.isEmpty()) {
                // Dead — no transition defined
                steps.add(new StepDetail(i + 2, symbol, Set.of(),
                        "No transition from " + currentState + " on '" + symbol + "' → DEAD"));
                return new SimulationResult(false, steps);
            }

            // DFA: exactly one target
            currentState = targets.iterator().next();
            steps.add(new StepDetail(i + 2, symbol, Set.of(currentState),
                    "Read '" + symbol + "' → move to " + currentState));
        }

        boolean accepted = a.getFinalStateIds().contains(currentState);
        steps.add(new StepDetail(steps.size() + 1, "", Set.of(currentState),
                "Input consumed. State " + currentState + " is " +
                        (accepted ? "FINAL → ACCEPT" : "not final → REJECT")));

        return new SimulationResult(accepted, steps);
    }

    /**
     * Simulate a string on an NFA (no ε-transitions).
     * Tracks set of current states, explores all branches per symbol.
     */
    public static SimulationResult simulateNFA(Automaton a, String input) {
        List<StepDetail> steps = new ArrayList<>();
        String startId = a.getStartStateId();

        if (startId == null) {
            return new SimulationResult(false, List.of(
                    new StepDetail(1, "", Set.of(), "No start state defined")));
        }

        Set<String> currentStates = new LinkedHashSet<>();
        currentStates.add(startId);

        steps.add(new StepDetail(1, "", Set.copyOf(currentStates),
                "Start in state(s) {" + String.join(", ", currentStates) + "}"));

        for (int i = 0; i < input.length(); i++) {
            String symbol = String.valueOf(input.charAt(i));
            Set<String> nextStates = new LinkedHashSet<>();

            for (String stateId : currentStates) {
                nextStates.addAll(a.getTargetStates(stateId, symbol));
            }

            currentStates = nextStates;

            if (currentStates.isEmpty()) {
                steps.add(new StepDetail(i + 2, symbol, Set.of(),
                        "Read '" + symbol + "' → no reachable states → DEAD"));
                return new SimulationResult(false, steps);
            }

            steps.add(new StepDetail(i + 2, symbol, Set.copyOf(currentStates),
                    "Read '" + symbol + "' → states {" + String.join(", ", currentStates) + "}"));
        }

        boolean accepted = currentStates.stream()
                .anyMatch(s -> a.getFinalStateIds().contains(s));

        steps.add(new StepDetail(steps.size() + 1, "", Set.copyOf(currentStates),
                "Input consumed. " + (accepted ? "Contains final state → ACCEPT" : "No final state → REJECT")));

        return new SimulationResult(accepted, steps);
    }

    /**
     * Simulate a string on an ε-NFA.
     * Computes ε-closure before the first symbol and after each transition.
     */
    public static SimulationResult simulateEpsilonNFA(Automaton a, String input) {
        List<StepDetail> steps = new ArrayList<>();
        String startId = a.getStartStateId();

        if (startId == null) {
            return new SimulationResult(false, List.of(
                    new StepDetail(1, "", Set.of(), "No start state defined")));
        }

        // Initial ε-closure
        Set<String> currentStates = epsilonClosure(a, Set.of(startId));

        steps.add(new StepDetail(1, "", Set.copyOf(currentStates),
                "ε-closure of start state " + startId + " = {" +
                        String.join(", ", currentStates) + "}"));

        for (int i = 0; i < input.length(); i++) {
            String symbol = String.valueOf(input.charAt(i));

            // Move: find all states reachable on this symbol
            Set<String> moved = new LinkedHashSet<>();
            for (String stateId : currentStates) {
                moved.addAll(a.getTargetStates(stateId, symbol));
            }

            // ε-closure of the moved states
            currentStates = epsilonClosure(a, moved);

            if (currentStates.isEmpty()) {
                steps.add(new StepDetail(i + 2, symbol, Set.of(),
                        "Read '" + symbol + "' → move → ε-closure = ∅ → DEAD"));
                return new SimulationResult(false, steps);
            }

            steps.add(new StepDetail(i + 2, symbol, Set.copyOf(currentStates),
                    "Read '" + symbol + "' → move → ε-closure = {" +
                            String.join(", ", currentStates) + "}"));
        }

        boolean accepted = currentStates.stream()
                .anyMatch(s -> a.getFinalStateIds().contains(s));

        steps.add(new StepDetail(steps.size() + 1, "", Set.copyOf(currentStates),
                "Input consumed. " + (accepted ? "Contains final state → ACCEPT" : "No final state → REJECT")));

        return new SimulationResult(accepted, steps);
    }

    // =========================================================================
    // ε-Closure (BFS)
    // =========================================================================

    /**
     * Computes the ε-closure of a set of states: all states reachable
     * from the given states via zero or more ε-transitions.
     *
     * <p>This is a BFS traversal following only ε-edges. It is also used
     * by {@link SubsetConstruction}.</p>
     */
    public static Set<String> epsilonClosure(Automaton a, Set<String> stateIds) {
        Set<String> closure = new LinkedHashSet<>(stateIds);
        Queue<String> worklist = new LinkedList<>(stateIds);

        while (!worklist.isEmpty()) {
            String current = worklist.poll();
            for (Transition t : a.getTransitionsFrom(current)) {
                if (t.isEpsilon() && !closure.contains(t.targetStateId())) {
                    closure.add(t.targetStateId());
                    worklist.add(t.targetStateId());
                }
            }
        }

        return closure;
    }

    // =========================================================================
    // Dispatcher (called from handler)
    // =========================================================================

    /**
     * Dispatches simulation based on type string from the API request.
     */
    public static SimulationResult simulate(Automaton a, String input, String type) {
        return switch (type.toUpperCase()) {
            case "DFA"  -> simulateDFA(a, input);
            case "NFA"  -> simulateNFA(a, input);
            case "ENFA" -> simulateEpsilonNFA(a, input);
            default     -> throw new IllegalArgumentException("Unknown automaton type: " + type);
        };
    }
}
