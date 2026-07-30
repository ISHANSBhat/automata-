package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.ConversionStep;
import automata.engine.StepLogger.ConversionResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Subset Construction algorithm for converting NFA/ε-NFA to DFA.
 * Pure functions — takes an Automaton, returns a new Automaton + step trace.
 */
public final class SubsetConstruction {

    private SubsetConstruction() {}

    /**
     * Convert an NFA (no ε-transitions) to a DFA via subset construction.
     */
    public static ConversionResult convertNFAtoDFA(Automaton nfa) {
        return convert(nfa, false);
    }

    /**
     * Convert an ε-NFA to a DFA via subset construction with ε-closure.
     */
    public static ConversionResult convertENFAtoDFA(Automaton enfa) {
        return convert(enfa, true);
    }

    /**
     * Dispatch based on type string from API.
     */
    public static ConversionResult convert(Automaton automaton, String type) {
        return switch (type.toUpperCase()) {
            case "NFA_TO_DFA"  -> convertNFAtoDFA(automaton);
            case "ENFA_TO_DFA" -> convertENFAtoDFA(automaton);
            default -> throw new IllegalArgumentException("Unknown conversion type: " + type);
        };
    }

    // =========================================================================
    // Core Algorithm
    // =========================================================================

    private static ConversionResult convert(Automaton nfa, boolean hasEpsilon) {
        List<ConversionStep> steps = new ArrayList<>();
        Automaton dfa = new Automaton();

        String startId = nfa.getStartStateId();
        if (startId == null) {
            return new ConversionResult(dfa, List.of(
                    new ConversionStep(1, Set.of(), "", "No start state defined")));
        }

        // Alphabet (non-ε symbols)
        Set<String> alphabet = nfa.getAlphabet();
        for (String sym : alphabet) {
            dfa.addAlphabetSymbol(sym);
        }

        // 1. Compute initial DFA state
        Set<String> initialSet;
        if (hasEpsilon) {
            initialSet = SimulationEngine.epsilonClosure(nfa, Set.of(startId));
        } else {
            initialSet = new LinkedHashSet<>();
            initialSet.add(startId);
        }

        String initialName = dfaStateName(initialSet);

        steps.add(new ConversionStep(1, Set.copyOf(initialSet), initialName,
                (hasEpsilon
                        ? "ε-closure of start state {" + startId + "} = " + initialName
                        : "Initial DFA state = " + initialName)));

        // Map from NFA state set → DFA state name
        Map<Set<String>, String> stateMap = new LinkedHashMap<>();
        stateMap.put(initialSet, initialName);

        // Worklist of unprocessed DFA states
        Queue<Set<String>> worklist = new LinkedList<>();
        worklist.add(initialSet);

        // Add initial DFA state
        boolean initialIsFinal = initialSet.stream()
                .anyMatch(s -> nfa.getFinalStateIds().contains(s));
        dfa.addState(new State(initialName, initialName, true, initialIsFinal));
        if (initialIsFinal) dfa.addFinalStateId(initialName);
        dfa.setStartStateId(initialName);

        int stepNum = 2;

        // 2. Process worklist
        while (!worklist.isEmpty()) {
            Set<String> currentSet = worklist.poll();
            String currentName = stateMap.get(currentSet);

            for (String symbol : alphabet) {
                // Compute move(currentSet, symbol)
                Set<String> moveSet = new LinkedHashSet<>();
                for (String stateId : currentSet) {
                    moveSet.addAll(nfa.getTargetStates(stateId, symbol));
                }

                // If ε-NFA, apply ε-closure to the moved states
                Set<String> targetSet;
                if (hasEpsilon) {
                    targetSet = SimulationEngine.epsilonClosure(nfa, moveSet);
                } else {
                    targetSet = moveSet;
                }

                if (targetSet.isEmpty()) {
                    // Dead state — skip (no transition in DFA)
                    continue;
                }

                String targetName = dfaStateName(targetSet);

                // Is this a new DFA state?
                if (!stateMap.containsKey(targetSet)) {
                    stateMap.put(targetSet, targetName);
                    worklist.add(targetSet);

                    boolean isFinal = targetSet.stream()
                            .anyMatch(s -> nfa.getFinalStateIds().contains(s));
                    dfa.addState(new State(targetName, targetName, false, isFinal));
                    if (isFinal) dfa.addFinalStateId(targetName);
                }

                // Add transition
                dfa.addTransition(new Transition(currentName, stateMap.get(targetSet), symbol));

                String desc = hasEpsilon
                        ? "δ(" + currentName + ", " + symbol + ") = move → ε-closure = " + targetName
                        : "δ(" + currentName + ", " + symbol + ") = " + targetName;

                steps.add(new ConversionStep(stepNum++, Set.copyOf(targetSet),
                        stateMap.get(targetSet), desc));
            }
        }

        steps.add(new ConversionStep(stepNum, Set.of(), "",
                "Subset construction complete. DFA has " + dfa.getStates().size() + " states."));

        return new ConversionResult(dfa, steps);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Generates a DFA state name from a set of NFA state IDs.
     * e.g. {q0, q1, q3} → "{q0,q1,q3}"
     */
    private static String dfaStateName(Set<String> nfaStates) {
        if (nfaStates.isEmpty()) return "∅";
        List<String> sorted = nfaStates.stream().sorted().toList();
        return "{" + String.join(",", sorted) + "}";
    }
}
