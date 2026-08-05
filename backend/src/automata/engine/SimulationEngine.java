package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.StepDetail;
import automata.engine.StepLogger.SimulationResult;

import java.util.*;

/**
 * Pure-function simulation engine for DFA, NFA, and ε-NFA.
 * Each method takes an Automaton + input string and returns a SimulationResult
 * with accept/reject status and step-by-step trace. No side effects.
 *
 * <p>Also contains utility algorithms matching the JS FALib:</p>
 * <ul>
 *   <li>{@link #removeEpsilon} — ε-NFA → NFA</li>
 *   <li>{@link #makeComplete} — adds a dead state for missing transitions</li>
 *   <li>{@link #testString} — unified simulation (auto-detects type)</li>
 *   <li>{@link #languageKind} — determines if the language is empty, finite, or infinite</li>
 *   <li>{@link #enumerateStrings} — BFS enumeration of accepted strings</li>
 * </ul>
 */
public final class SimulationEngine {

    private SimulationEngine() {}

    // =========================================================================
    // Public API — Simulation
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
    // Unified simulation (matches JS FALib.testString)
    // =========================================================================

    /**
     * Unified string test that auto-detects ε-transitions.
     * Matches JS {@code FALib.testString(a, input)}.
     *
     * @return SimulationResult with accepted flag and step-by-step trace
     */
    public static SimulationResult testString(Automaton a, String input) {
        String startId = a.getStartStateId();
        if (startId == null) {
            return new SimulationResult(false, List.of(
                    new StepDetail(1, "", Set.of(), "No start state defined")));
        }

        boolean hasEps = a.hasEpsilonTransitions();
        List<String> active;
        if (hasEps) {
            active = new ArrayList<>(epsilonClosure(a, Set.of(startId)));
        } else {
            active = new ArrayList<>();
            active.add(startId);
        }

        List<StepDetail> steps = new ArrayList<>();
        steps.add(new StepDetail(1, "", Set.copyOf(active),
                "Initial active states: {" + String.join(", ", active) + "}"));

        for (int i = 0; i < input.length(); i++) {
            String c = String.valueOf(input.charAt(i));
            Set<String> nxt = move(a, active, c);
            if (hasEps) {
                active = new ArrayList<>(epsilonClosure(a, nxt));
            } else {
                active = new ArrayList<>(nxt);
            }
            steps.add(new StepDetail(i + 2, c, Set.copyOf(active),
                    "Read '" + c + "' → active {" + String.join(", ", active) + "}"));
        }

        boolean accepted = active.stream()
                .anyMatch(id -> a.getFinalStateIds().contains(id));

        return new SimulationResult(accepted, steps);
    }

    // =========================================================================
    // ε-Closure (BFS)
    // =========================================================================

    /**
     * Computes the ε-closure of a set of states: all states reachable
     * from the given states via zero or more ε-transitions.
     * Matches JS {@code FALib.epsilonClosure(a, stateIds)}.
     */
    public static Set<String> epsilonClosure(Automaton a, Set<String> stateIds) {
        Set<String> closure = new LinkedHashSet<>(stateIds);
        Deque<String> stack = new ArrayDeque<>(stateIds);

        // Build ε-adjacency map
        Map<String, List<String>> epsAdj = new HashMap<>();
        for (Transition t : a.getTransitions()) {
            if (t.isEpsilon()) {
                epsAdj.computeIfAbsent(t.sourceStateId(), k -> new ArrayList<>())
                        .add(t.targetStateId());
            }
        }

        while (!stack.isEmpty()) {
            String s = stack.pop();
            for (String nxt : epsAdj.getOrDefault(s, List.of())) {
                if (!closure.contains(nxt)) {
                    closure.add(nxt);
                    stack.push(nxt);
                }
            }
        }

        return closure;
    }

    // =========================================================================
    // Move function (matches JS FALib.move)
    // =========================================================================

    /**
     * Compute the set of states reachable from a set of active states on a given symbol.
     * Matches JS {@code FALib.move(a, stateIds, symbol)}.
     */
    public static Set<String> move(Automaton a, Collection<String> stateIds, String symbol) {
        Set<String> out = new LinkedHashSet<>();
        for (Transition t : a.getTransitions()) {
            if (t.symbol().equals(symbol) && stateIds.contains(t.sourceStateId())) {
                out.add(t.targetStateId());
            }
        }
        return out;
    }

    // =========================================================================
    // ε-Removal (matches JS FALib.removeEpsilon)
    // =========================================================================

    /**
     * Removes ε-transitions from an automaton, producing an equivalent NFA.
     * Matches JS {@code FALib.removeEpsilon(a)}.
     *
     * <p>For each state p, computes ε-closure(p). For each state q in that closure,
     * copies all non-ε transitions from q to p. If any state in ε-closure(p) is
     * final, p becomes final.</p>
     */
    public static Automaton removeEpsilon(Automaton a) {
        // Compute ε-closures for each state
        Map<String, Set<String>> closures = new LinkedHashMap<>();
        for (State s : a.getStates()) {
            closures.put(s.id(), epsilonClosure(a, Set.of(s.id())));
        }

        // Build new transitions
        List<Transition> newTrans = new ArrayList<>();
        for (State p : a.getStates()) {
            for (String q : closures.get(p.id())) {
                for (Transition t : a.getTransitions()) {
                    if (t.isEpsilon() || !t.sourceStateId().equals(q)) continue;
                    newTrans.add(new Transition(p.id(), t.targetStateId(), t.symbol()));
                }
            }
        }

        // Determine new final states
        Set<String> finalIds = new LinkedHashSet<>();
        for (State s : a.getStates()) {
            boolean isFinal = closures.get(s.id()).stream()
                    .anyMatch(id -> a.getFinalStateIds().contains(id));
            if (isFinal) finalIds.add(s.id());
        }

        // Build result automaton
        Automaton result = new Automaton();
        for (State s : a.getStates()) {
            boolean isFinal = finalIds.contains(s.id());
            result.addState(new State(s.id(), s.name(), s.isStart(), isFinal));
            if (s.isStart()) result.setStartStateId(s.id());
            if (isFinal) result.addFinalStateId(s.id());
        }
        for (Transition t : newTrans) {
            result.addTransition(t);
        }
        return result;
    }

    // =========================================================================
    // Make Complete (matches JS FALib.makeComplete)
    // =========================================================================

    /**
     * Makes a DFA complete by adding a dead state for any missing transitions.
     * Matches JS {@code FALib.makeComplete(a)}.
     */
    public static Automaton makeComplete(Automaton a) {
        List<String> alphabet = a.getAlphabetComputed();
        if (alphabet.isEmpty()) {
            // No alphabet — nothing to complete
            Automaton copy = new Automaton();
            for (State s : a.getStates()) {
                copy.addState(s);
                if (s.isStart()) copy.setStartStateId(s.id());
                if (s.isFinal()) copy.addFinalStateId(s.id());
            }
            for (Transition t : a.getTransitions()) copy.addTransition(t);
            return copy;
        }

        // Copy states and transitions
        List<State> stateList = new ArrayList<>(a.getStates());
        List<Transition> transList = new ArrayList<>(a.getTransitions());

        // Check which (state, symbol) pairs are missing
        String deadId = "DEAD";
        boolean needsDead = false;

        for (State s : stateList) {
            for (String c : alphabet) {
                boolean has = transList.stream()
                        .anyMatch(t -> t.sourceStateId().equals(s.id()) && t.symbol().equals(c));
                if (!has) {
                    if (!needsDead) {
                        needsDead = true;
                    }
                    transList.add(new Transition(s.id(), deadId, c));
                }
            }
        }

        if (needsDead) {
            stateList.add(new State(deadId, "☠", false, false));
            // Dead state loops to itself on all symbols
            for (String c : alphabet) {
                transList.add(new Transition(deadId, deadId, c));
            }
        }

        // Build result
        Automaton result = new Automaton();
        for (State s : stateList) {
            result.addState(s);
            if (s.isStart()) result.setStartStateId(s.id());
            if (s.isFinal()) result.addFinalStateId(s.id());
        }
        for (Transition t : transList) {
            result.addTransition(t);
        }
        return result;
    }

    // =========================================================================
    // Reachability (matches JS FALib.reachableFrom)
    // =========================================================================

    /**
     * BFS reachability from a given start state.
     * Matches JS {@code FALib.reachableFrom(a, startId)}.
     */
    public static Set<String> reachableFrom(Automaton a, String startId) {
        if (startId == null) return Set.of();
        Set<String> seen = new LinkedHashSet<>();
        seen.add(startId);
        Deque<String> stack = new ArrayDeque<>();
        stack.push(startId);

        // Build adjacency
        Map<String, List<String>> adj = new HashMap<>();
        for (Transition t : a.getTransitions()) {
            adj.computeIfAbsent(t.sourceStateId(), k -> new ArrayList<>())
                    .add(t.targetStateId());
        }

        while (!stack.isEmpty()) {
            String s = stack.pop();
            for (String nxt : adj.getOrDefault(s, List.of())) {
                if (!seen.contains(nxt)) {
                    seen.add(nxt);
                    stack.push(nxt);
                }
            }
        }
        return seen;
    }

    // =========================================================================
    // Language Kind (matches JS FALib.languageKind)
    // =========================================================================

    /**
     * Determines if the language is empty, finite, or infinite.
     * Matches JS {@code FALib.languageKind(a)}.
     *
     * @return "empty", "finite", or "infinite"
     */
    public static String languageKind(Automaton a) {
        String startId = a.getStartStateId();
        if (startId == null) return "empty";

        Set<String> reach = reachableFrom(a, startId);

        // Check if any final state is reachable
        boolean finalReachable = reach.stream()
                .anyMatch(id -> a.getFinalStateIds().contains(id));
        if (!finalReachable) return "empty";

        // Build adjacency restricted to reachable states
        Map<String, List<String>> adj = new HashMap<>();
        for (Transition t : a.getTransitions()) {
            adj.computeIfAbsent(t.sourceStateId(), k -> new ArrayList<>())
                    .add(t.targetStateId());
        }

        // DFS cycle detection: if a cycle exists on a path that can reach a final state, infinite
        Map<String, Integer> color = new HashMap<>(); // 0=unvisited, 1=in-progress, 2=done
        boolean[] infinite = {false};

        class DFS {
            boolean dfs(String u) {
                color.put(u, 1);
                for (String v : adj.getOrDefault(u, List.of())) {
                    if (!reach.contains(v)) continue;
                    Integer c = color.get(v);
                    if (c != null && c == 1) {
                        // Found a cycle — check if a final state is reachable from u
                        Set<String> cycReach = reachableFrom(a, u);
                        if (cycReach.stream().anyMatch(id -> a.getFinalStateIds().contains(id))) {
                            return true;
                        }
                    } else if (c == null && dfs(v)) {
                        return true;
                    }
                }
                color.put(u, 2);
                return false;
            }
        }

        DFS dfs = new DFS();
        return dfs.dfs(startId) ? "infinite" : "finite";
    }

    // =========================================================================
    // String Enumeration (matches JS FALib.enumerateStrings)
    // =========================================================================

    /**
     * BFS-based enumeration of accepted strings.
     * Matches JS {@code FALib.enumerateStrings(a, limit, maxPops)}.
     */
    public static List<String> enumerateStrings(Automaton a, int limit, int maxPops) {
        List<String> alphabet = a.getAlphabetComputed();
        String startId = a.getStartStateId();
        if (startId == null) return List.of();

        if (alphabet.isEmpty()) {
            // No alphabet — only ε could be accepted
            return a.getFinalStateIds().contains(startId)
                    ? List.of("ε") : List.of();
        }

        boolean hasEps = a.hasEpsilonTransitions();
        List<String> init;
        if (hasEps) {
            init = new ArrayList<>(epsilonClosure(a, Set.of(startId)));
        } else {
            init = new ArrayList<>();
            init.add(startId);
        }

        Set<String> found = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();

        // Queue entries: active state set + accumulated string
        Deque<Object[]> queue = new ArrayDeque<>();
        List<String> sortedInit = new ArrayList<>(init);
        Collections.sort(sortedInit);
        String initSig = ":" + String.join(",", sortedInit);
        seen.add(initSig);
        queue.add(new Object[]{init, ""});

        int pops = 0;
        while (!queue.isEmpty() && found.size() < limit && pops < maxPops) {
            Object[] entry = queue.poll();
            @SuppressWarnings("unchecked")
            List<String> active = (List<String>) entry[0];
            String str = (String) entry[1];
            pops++;

            // Check if any active state is final
            boolean accepted = active.stream()
                    .anyMatch(id -> a.getFinalStateIds().contains(id));
            if (accepted) {
                found.add(str.isEmpty() ? "ε" : str);
                if (found.size() >= limit) break;
            }

            // Expand
            for (String c : alphabet) {
                Set<String> nxt = move(a, active, c);
                if (nxt.isEmpty()) continue;
                List<String> expanded;
                if (hasEps) {
                    expanded = new ArrayList<>(epsilonClosure(a, nxt));
                } else {
                    expanded = new ArrayList<>(nxt);
                }
                List<String> sortedExpanded = new ArrayList<>(expanded);
                Collections.sort(sortedExpanded);
                String sig = str + c + ":" + String.join(",", sortedExpanded);
                if (seen.contains(sig)) continue;
                seen.add(sig);
                queue.add(new Object[]{expanded, str + c});
            }
        }

        return new ArrayList<>(found);
    }

    /** Convenience overload with default limit and maxPops. */
    public static List<String> enumerateStrings(Automaton a) {
        return enumerateStrings(a, 10, 6000);
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
