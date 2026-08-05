package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.ConversionStep;
import automata.engine.StepLogger.ConversionResult;

import java.util.*;

/**
 * Subset Construction algorithm for converting NFA/ε-NFA to DFA.
 * Formats transition calculations systematically and maps each NFA subset to a DFA state.
 */
public final class SubsetConstruction {

    private SubsetConstruction() {}

    public static ConversionResult convertNFAtoDFA(Automaton nfa) {
        return convert(nfa, "NFA_TO_DFA");
    }

    public static ConversionResult convertENFAtoDFA(Automaton enfa) {
        return convert(enfa, "ENFA_TO_DFA");
    }

    public static ConversionResult convert(Automaton automaton, String type) {
        List<ConversionStep> steps = new ArrayList<>();
        Automaton src = automaton;

        String startId = src.getStartStateId();
        if (startId == null) {
            return new ConversionResult(new Automaton(), List.of(
                    new ConversionStep(1, Set.of(), "", "No start state defined in input automaton")));
        }

        List<String> alphabet = src.getAlphabetComputed();
        if (alphabet.isEmpty()) {
            // Alphabet might be empty or specified on input
            Set<String> explicitAlpha = src.getAlphabet();
            if (!explicitAlpha.isEmpty()) {
                alphabet = new ArrayList<>(explicitAlpha);
                Collections.sort(alphabet);
            }
        }

        Map<String, String> subsetMap = new LinkedHashMap<>();
        List<List<String>> subsets = new ArrayList<>();
        List<State> dfaStates = new ArrayList<>();
        List<Transition> dfaTrans = new ArrayList<>();
        Set<String> finalIds = src.getFinalStateIds();

        // Key function: sorts state IDs to form a canonical string representation
        java.util.function.Function<List<String>, String> key = sub -> {
            List<String> sorted = new ArrayList<>(sub);
            Collections.sort(sorted);
            return String.join(",", sorted);
        };

        // Systematic state naming function: D0, D1, D2... with alphabetical labels A, B, C...
        java.util.function.Function<List<String>, String> idOf = sub -> {
            String k = key.apply(sub);
            if (subsetMap.containsKey(k)) return subsetMap.get(k);
            int idx = subsets.size();
            String id = "D" + idx;
            String label = String.valueOf((char) (65 + (idx % 26))) + (idx >= 26 ? (idx / 26) : "");
            String fullName = label + " ({" + k + "})";

            subsetMap.put(k, id);
            subsets.add(sub);
            dfaStates.add(new State(id, fullName, false, false));
            return id;
        };

        // Initial DFA state: ε-closure of NFA start state
        Set<String> startClosureSet = SimulationEngine.epsilonClosure(src, Set.of(startId));
        List<String> initSet = new ArrayList<>(startClosureSet);
        Collections.sort(initSet);

        String startDfaId = idOf.apply(initSet);
        int stepNum = 1;

        steps.add(new ConversionStep(stepNum++, Set.copyOf(initSet), startDfaId,
                "Initial DFA State " + startDfaId + " = ε-closure({" + startId + "}) = {" + String.join(", ", initSet) + "}"));

        int si = 0;
        while (si < subsets.size()) {
            List<String> sub = subsets.get(si++);
            String fromId = subsetMap.get(key.apply(sub));
            State fromState = dfaStates.stream().filter(s -> s.id().equals(fromId)).findFirst().orElse(null);
            String fromName = fromState != null ? fromState.name() : fromId;

            for (String c : alphabet) {
                Set<String> moved = SimulationEngine.move(src, sub, c);
                Set<String> closed = SimulationEngine.epsilonClosure(src, moved);
                List<String> nxt = new ArrayList<>(closed);
                Collections.sort(nxt);

                if (nxt.isEmpty()) {
                    steps.add(new ConversionStep(stepNum++, Set.of(), "",
                            "δ(" + fromId + " [" + fromName + "], '" + c + "') = ε-closure(Move({" + String.join(", ", sub) + "}, '" + c + "')) = ∅ (No transition)"));
                    continue;
                }

                String toId = idOf.apply(nxt);
                State toState = dfaStates.stream().filter(s -> s.id().equals(toId)).findFirst().orElse(null);
                String toName = toState != null ? toState.name() : toId;

                dfaTrans.add(new Transition(fromId, toId, c));

                steps.add(new ConversionStep(stepNum++, Set.copyOf(nxt), toId,
                        "δ(" + fromId + " [" + fromName + "], '" + c + "') = ε-closure(Move({" + String.join(", ", sub) + "}, '" + c + "')) = {" + String.join(", ", nxt) + "} → State " + toId + " [" + toName + "]"));
            }
        }

        // Build final DFA Automaton systematically
        Automaton dfa = new Automaton();
        for (int i = 0; i < dfaStates.size(); i++) {
            State s = dfaStates.get(i);
            boolean isStart = s.id().equals(startDfaId);
            boolean isFinal = subsets.get(i).stream().anyMatch(finalIds::contains);

            dfa.addState(new State(s.id(), s.name(), isStart, isFinal));
            if (isStart) dfa.setStartStateId(s.id());
            if (isFinal) dfa.addFinalStateId(s.id());
        }

        for (String c : alphabet) {
            dfa.addAlphabetSymbol(c);
        }

        for (Transition t : dfaTrans) {
            dfa.addTransition(t);
        }

        steps.add(new ConversionStep(stepNum, Set.of(), "",
                "Subset construction complete. Constructed DFA has " + dfa.getStates().size() + " states and " + dfa.getTransitions().size() + " transitions."));

        return new ConversionResult(dfa, steps);
    }
}
