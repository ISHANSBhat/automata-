package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DFA Minimization using the table-filling (Myhill-Nerode) algorithm.
 *
 * <p>Algorithm outline:</p>
 * <ol>
 *   <li>Complete the DFA by adding an explicit dead/trap state for missing transitions</li>
 *   <li>Mark all (final, non-final) pairs as distinguishable</li>
 *   <li>Iterate until no new marks: for each unmarked pair (p, q), if δ(p, a) and δ(q, a)
 *       lead to an already-distinguishable pair for some symbol a, mark (p, q)</li>
 *   <li>Merge indistinguishable (unmarked) states into equivalence classes</li>
 *   <li>Build a new minimized DFA from the equivalence classes</li>
 *   <li>Remove the dead state if it is unreachable from the start state</li>
 * </ol>
 */
public final class DFAMinimization {

    private DFAMinimization() {}

    /**
     * Minimize a DFA. The input must already be a DFA (no ε-transitions,
     * at most one transition per state/symbol pair).
     *
     * @param dfa the DFA to minimize
     * @return a new minimized DFA accepting the same language
     */
    public static Automaton minimize(Automaton dfa) {
        if (dfa.getStates().isEmpty() || dfa.getStartStateId() == null) {
            return dfa;
        }

        Set<String> alphabet = dfa.getAlphabet();
        if (alphabet.isEmpty()) {
            // No alphabet → the language is either {ε} or ∅
            // Just keep start state (and mark it final if it was)
            Automaton result = new Automaton();
            String startId = dfa.getStartStateId();
            boolean isFinal = dfa.getFinalStateIds().contains(startId);
            result.addState(new State(startId, startId, true, isFinal));
            result.setStartStateId(startId);
            if (isFinal) result.addFinalStateId(startId);
            return result;
        }

        // =====================================================================
        // Step 0: Remove unreachable states
        // =====================================================================
        Set<String> reachable = findReachableStates(dfa);

        // =====================================================================
        // Step 1: Complete the DFA (add dead state for missing transitions)
        // =====================================================================
        String deadState = "__dead__";
        boolean needsDead = false;

        // Check if we need a dead state
        for (String stateId : reachable) {
            for (String sym : alphabet) {
                Set<String> targets = dfa.getTargetStates(stateId, sym);
                if (targets.isEmpty()) {
                    needsDead = true;
                    break;
                }
            }
            if (needsDead) break;
        }

        // Build a complete transition function as a map
        // transFunc[stateId][symbol] = targetStateId
        List<String> stateList = new ArrayList<>(reachable);
        if (needsDead) {
            stateList.add(deadState);
        }

        Map<String, Map<String, String>> transFunc = new LinkedHashMap<>();
        for (String stateId : stateList) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String sym : alphabet) {
                if (stateId.equals(deadState)) {
                    row.put(sym, deadState);
                } else {
                    Set<String> targets = dfa.getTargetStates(stateId, sym);
                    if (targets.isEmpty()) {
                        row.put(sym, deadState);
                    } else {
                        String target = targets.iterator().next();
                        // If the target is unreachable (shouldn't happen but be safe)
                        row.put(sym, reachable.contains(target) ? target : deadState);
                    }
                }
            }
            transFunc.put(stateId, row);
        }

        Set<String> finalStates = new LinkedHashSet<>();
        for (String sid : stateList) {
            if (dfa.getFinalStateIds().contains(sid)) {
                finalStates.add(sid);
            }
        }

        // =====================================================================
        // Step 2: Table-filling — mark distinguishable pairs
        // =====================================================================
        int n = stateList.size();
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            indexMap.put(stateList.get(i), i);
        }

        // distinguishable[i][j] for i < j
        boolean[][] distinguishable = new boolean[n][n];

        // Initial marking: (final, non-final) pairs
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean iFinal = finalStates.contains(stateList.get(i));
                boolean jFinal = finalStates.contains(stateList.get(j));
                if (iFinal != jFinal) {
                    distinguishable[i][j] = true;
                }
            }
        }

        // Iterate until convergence
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (distinguishable[i][j]) continue;

                    String si = stateList.get(i);
                    String sj = stateList.get(j);

                    for (String sym : alphabet) {
                        String ti = transFunc.get(si).get(sym);
                        String tj = transFunc.get(sj).get(sym);

                        if (ti.equals(tj)) continue;

                        int a = indexMap.get(ti);
                        int b = indexMap.get(tj);
                        int lo = Math.min(a, b);
                        int hi = Math.max(a, b);

                        if (distinguishable[lo][hi]) {
                            distinguishable[i][j] = true;
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        // =====================================================================
        // Step 3: Build equivalence classes (union-find style)
        // =====================================================================
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!distinguishable[i][j]) {
                    union(parent, i, j);
                }
            }
        }

        // Group states by their equivalence class representative
        Map<Integer, List<String>> classes = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            classes.computeIfAbsent(root, k -> new ArrayList<>()).add(stateList.get(i));
        }

        // =====================================================================
        // Step 4: Build the minimized DFA
        // =====================================================================
        Automaton minimized = new Automaton();

        // Map each original state to its equivalence class name
        Map<String, String> stateToClass = new LinkedHashMap<>();
        Map<String, List<String>> classMembers = new LinkedHashMap<>();

        for (var entry : classes.entrySet()) {
            List<String> members = entry.getValue();
            // Use the first member's name as the class name, prefer the original name
            // For readability, pick a descriptive name
            String className = pickClassName(members, dfa);
            for (String member : members) {
                stateToClass.put(member, className);
            }
            classMembers.put(className, members);
        }

        // Add states
        String startId = dfa.getStartStateId();
        String startClass = stateToClass.get(startId);

        for (var entry : classMembers.entrySet()) {
            String className = entry.getKey();
            List<String> members = entry.getValue();

            boolean isStart = className.equals(startClass);
            boolean isFinal = members.stream().anyMatch(finalStates::contains);

            // Skip the dead state class if it's not final and not start
            if (members.contains(deadState) && !isFinal && !isStart) {
                continue;
            }

            minimized.addState(new State(className, className, isStart, isFinal));
            if (isStart) minimized.setStartStateId(className);
            if (isFinal) minimized.addFinalStateId(className);
        }

        // Add alphabet
        for (String sym : alphabet) {
            minimized.addAlphabetSymbol(sym);
        }

        // Add transitions
        Set<String> addedTransitions = new HashSet<>(); // avoid duplicates
        for (var entry : classMembers.entrySet()) {
            String srcClass = entry.getKey();
            List<String> members = entry.getValue();

            // Skip dead state class
            if (members.contains(deadState) && !finalStates.stream().anyMatch(members::contains)
                    && !srcClass.equals(startClass)) {
                continue;
            }

            // Use the first member as representative
            String representative = members.get(0);

            for (String sym : alphabet) {
                String target = transFunc.get(representative).get(sym);
                String targetClass = stateToClass.get(target);

                // Skip transitions to the dead state class
                if (classMembers.containsKey(targetClass)
                        && classMembers.get(targetClass).contains(deadState)
                        && !finalStates.stream().anyMatch(classMembers.get(targetClass)::contains)
                        && !targetClass.equals(startClass)) {
                    continue;
                }

                String transKey = srcClass + "|" + sym + "|" + targetClass;
                if (addedTransitions.add(transKey)) {
                    minimized.addTransition(new Transition(srcClass, targetClass, sym));
                }
            }
        }

        return minimized;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Find all states reachable from the start state via BFS. */
    private static Set<String> findReachableStates(Automaton dfa) {
        Set<String> reachable = new LinkedHashSet<>();
        String start = dfa.getStartStateId();
        if (start == null) return reachable;

        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        reachable.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Transition t : dfa.getTransitionsFrom(current)) {
                if (reachable.add(t.targetStateId())) {
                    queue.add(t.targetStateId());
                }
            }
        }
        return reachable;
    }

    /** Pick a readable class name from a list of equivalent states. */
    private static String pickClassName(List<String> members, Automaton dfa) {
        // Prefer the original start state name, then shortest name
        String startId = dfa.getStartStateId();
        if (members.contains(startId)) {
            State s = dfa.getState(startId);
            return s != null ? s.name() : startId;
        }
        // Pick the member with the shortest name (most readable)
        return members.stream()
                .map(id -> {
                    State s = dfa.getState(id);
                    return s != null ? s.name() : id;
                })
                .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .orElse(members.get(0));
    }

    // --- Union-Find ---

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]]; // path compression
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}
