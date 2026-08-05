package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;

import java.util.*;

/**
 * DFA Minimization using partition refinement matching JS FALib.minimize logic.
 */
public final class DFAMinimization {

    private DFAMinimization() {}

    /**
     * Minimize a DFA matching JS FALib.minimize(a).
     */
    public static Automaton minimize(Automaton a) {
        Automaton src = a;
        if (src.hasEpsilonTransitions()) {
            src = SimulationEngine.removeEpsilon(a);
        }
        if (!src.isDFA()) return null;

        List<String> alphabet = src.getAlphabetComputed();
        Automaton complete = SimulationEngine.makeComplete(src);
        String startId = complete.getStartStateId();
        if (startId == null) {
            return new Automaton();
        }

        Set<String> reach = SimulationEngine.reachableFrom(complete, startId);
        List<State> used = complete.getStates().stream()
                .filter(s -> reach.contains(s.id()))
                .toList();

        Set<String> finSet = new HashSet<>();
        for (State s : used) {
            if (s.isFinal()) finSet.add(s.id());
        }

        List<List<String>> parts = new ArrayList<>();
        List<String> f0 = used.stream().filter(State::isFinal).map(State::id).toList();
        List<String> f1 = used.stream().filter(s -> !s.isFinal()).map(State::id).toList();
        if (!f0.isEmpty()) parts.add(new ArrayList<>(f0));
        if (!f1.isEmpty()) parts.add(new ArrayList<>(f1));

        if (alphabet.isEmpty()) {
            if (parts.isEmpty()) {
                parts = used.stream().map(s -> List.of(s.id())).toList();
            }
        }

        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 500) {
            changed = false;
            List<List<String>> nextParts = new ArrayList<>();

            for (List<String> part : parts) {
                Map<String, List<String>> groups = new LinkedHashMap<>();
                for (String id : part) {
                    List<String> sig = new ArrayList<>();
                    for (String c : alphabet) {
                        Transition t = complete.getTransitions().stream()
                                .filter(tt -> tt.sourceStateId().equals(id) && tt.symbol().equals(c))
                                .findFirst().orElse(null);
                        String target = t != null ? t.targetStateId() : id;

                        int targetPartIdx = -1;
                        for (int pIdx = 0; pIdx < parts.size(); pIdx++) {
                            if (parts.get(pIdx).contains(target)) {
                                targetPartIdx = pIdx;
                                break;
                            }
                        }
                        sig.add(String.valueOf(targetPartIdx));
                    }
                    String sk = String.join(",", sig);
                    groups.computeIfAbsent(sk, k -> new ArrayList<>()).add(id);
                }

                if (groups.size() > 1) changed = true;
                nextParts.addAll(groups.values());
            }
            parts = nextParts;
        }

        Map<String, String> idMap = new HashMap<>();
        List<State> newStates = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            List<String> p = parts.get(i);
            String nid = "M" + i;
            for (String old : p) idMap.put(old, nid);

            State rep = complete.getState(p.get(0));
            if (rep == null && !used.isEmpty()) rep = used.get(0);

            String label = "{" + String.join(",", p) + "}";
            boolean isStart = p.contains(startId);
            boolean isFinal = p.stream().anyMatch(finSet::contains);

            newStates.add(new State(nid, label, isStart, isFinal));
        }

        List<Transition> newTrans = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < parts.size(); i++) {
            List<String> p = parts.get(i);
            String from = "M" + i;
            State rep = complete.getState(p.get(0));
            if (rep == null) continue;

            for (String c : alphabet) {
                Transition t = complete.getTransitions().stream()
                        .filter(tt -> tt.sourceStateId().equals(rep.id()) && tt.symbol().equals(c))
                        .findFirst().orElse(null);
                if (t == null) continue;

                String to = idMap.get(t.targetStateId());
                String k = from + "\u0000" + c + "\u0000" + to;
                if (seen.contains(k)) continue;
                seen.add(k);
                newTrans.add(new Transition(from, to, c));
            }
        }

        Automaton result = new Automaton();
        for (State s : newStates) {
            result.addState(s);
            if (s.isStart()) result.setStartStateId(s.id());
            if (s.isFinal()) result.addFinalStateId(s.id());
        }
        for (Transition t : newTrans) {
            result.addTransition(t);
        }
        return result;
    }
}
