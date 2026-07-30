package automata.model;

import automata.util.JsonUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mutable representation of a finite automaton (DFA, NFA, or ε-NFA).
 *
 * <p>This is the core data structure that all engine algorithms operate on.
 * It is intentionally mutable so that algorithms like Thompson's construction
 * can build an automaton incrementally.</p>
 */
public class Automaton {

    private final Set<State> states;
    private final List<Transition> transitions;
    private final Set<String> alphabet;
    private String startStateId;
    private final Set<String> finalStateIds;

    // --- Constructors ----------------------------------------------------------

    /** Empty automaton. */
    public Automaton() {
        this.states = new LinkedHashSet<>();
        this.transitions = new ArrayList<>();
        this.alphabet = new TreeSet<>();
        this.startStateId = null;
        this.finalStateIds = new LinkedHashSet<>();
    }

    /** Full constructor for deserialization. */
    public Automaton(Set<State> states, List<Transition> transitions,
                     Set<String> alphabet, String startStateId,
                     Set<String> finalStateIds) {
        this.states = new LinkedHashSet<>(states);
        this.transitions = new ArrayList<>(transitions);
        this.alphabet = new TreeSet<>(alphabet);
        this.startStateId = startStateId;
        this.finalStateIds = new LinkedHashSet<>(finalStateIds);
    }

    // --- Accessors -------------------------------------------------------------

    public Set<State> getStates() {
        return Collections.unmodifiableSet(states);
    }

    public List<Transition> getTransitions() {
        return Collections.unmodifiableList(transitions);
    }

    public Set<String> getAlphabet() {
        return Collections.unmodifiableSet(alphabet);
    }

    public String getStartStateId() {
        return startStateId;
    }

    public Set<String> getFinalStateIds() {
        return Collections.unmodifiableSet(finalStateIds);
    }

    // --- Lookup ----------------------------------------------------------------

    /** Returns the State with the given id, or null if not found. */
    public State getState(String id) {
        for (State s : states) {
            if (s.id().equals(id)) return s;
        }
        return null;
    }

    /** Returns all transitions originating from the given state. */
    public List<Transition> getTransitionsFrom(String stateId) {
        return transitions.stream()
                .filter(t -> t.sourceStateId().equals(stateId))
                .toList();
    }

    /** Returns all transitions from a state on a specific symbol. */
    public List<Transition> getTransitionsForSymbol(String stateId, String symbol) {
        return transitions.stream()
                .filter(t -> t.sourceStateId().equals(stateId) && t.symbol().equals(symbol))
                .toList();
    }

    /** Returns the set of target state IDs reachable from stateId on the given symbol. */
    public Set<String> getTargetStates(String stateId, String symbol) {
        return getTransitionsForSymbol(stateId, symbol).stream()
                .map(Transition::targetStateId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // --- Mutators --------------------------------------------------------------

    public void addState(State state) {
        // Remove old version if present (same id, possibly different flags)
        states.remove(state);
        states.add(state);
        if (state.isStart()) {
            this.startStateId = state.id();
        }
        if (state.isFinal()) {
            finalStateIds.add(state.id());
        }
    }

    public void removeState(String stateId) {
        states.removeIf(s -> s.id().equals(stateId));
        transitions.removeIf(t ->
                t.sourceStateId().equals(stateId) || t.targetStateId().equals(stateId));
        finalStateIds.remove(stateId);
        if (stateId.equals(startStateId)) {
            startStateId = null;
        }
    }

    public void addTransition(Transition transition) {
        transitions.add(transition);
        if (!transition.isEpsilon()) {
            alphabet.add(transition.symbol());
        }
    }

    public void removeTransition(Transition transition) {
        transitions.remove(transition);
    }

    public void setStartStateId(String stateId) {
        this.startStateId = stateId;
    }

    public void addFinalStateId(String stateId) {
        finalStateIds.add(stateId);
    }

    public void removeFinalStateId(String stateId) {
        finalStateIds.remove(stateId);
    }

    public void addAlphabetSymbol(String symbol) {
        alphabet.add(symbol);
    }

    // --- Analysis --------------------------------------------------------------

    /**
     * Returns {@code true} if this automaton is deterministic:
     * <ul>
     *   <li>No ε-transitions</li>
     *   <li>At most one transition per (state, symbol) pair</li>
     * </ul>
     */
    public boolean isDFA() {
        for (Transition t : transitions) {
            if (t.isEpsilon()) return false;
        }
        for (State s : states) {
            for (String sym : alphabet) {
                if (getTransitionsForSymbol(s.id(), sym).size() > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if this automaton has any ε-transitions.
     */
    public boolean hasEpsilonTransitions() {
        return transitions.stream().anyMatch(Transition::isEpsilon);
    }

    // --- JSON serialization ----------------------------------------------------

    public String toJson() {
        // States array
        List<String> stateJsons = states.stream()
                .map(State::toJson)
                .toList();

        // Transitions array
        List<String> transJsons = transitions.stream()
                .map(Transition::toJson)
                .toList();

        // Alphabet array
        List<String> alphaJsons = alphabet.stream()
                .map(JsonUtil::quoted)
                .toList();

        // Final state IDs array
        List<String> finalJsons = finalStateIds.stream()
                .map(JsonUtil::quoted)
                .toList();

        return JsonUtil.objectOf(
                "states", JsonUtil.array(stateJsons),
                "transitions", JsonUtil.array(transJsons),
                "alphabet", JsonUtil.array(alphaJsons),
                "startStateId", startStateId == null ? "null" : JsonUtil.quoted(startStateId),
                "finalStateIds", JsonUtil.array(finalJsons)
        );
    }

    /**
     * Deserializes an Automaton from a JSON string (or a parsed JSON map).
     */
    public static Automaton fromJson(String json) {
        Map<String, Object> map = JsonUtil.parseObject(json);
        return fromJsonMap(map);
    }

    /**
     * Deserializes an Automaton from an already-parsed JSON map.
     */
    @SuppressWarnings("unchecked")
    public static Automaton fromJsonMap(Map<String, Object> map) {
        Automaton a = new Automaton();

        // Parse states
        List<Object> statesArr = JsonUtil.getArray(map, "states");
        for (Object sObj : statesArr) {
            if (sObj instanceof Map<?, ?> sMap) {
                Map<String, Object> sm = (Map<String, Object>) sMap;
                String id = JsonUtil.getString(sm, "id");
                String name = JsonUtil.getString(sm, "name");
                boolean isStart = JsonUtil.getBoolean(sm, "isStart");
                boolean isFinal = JsonUtil.getBoolean(sm, "isFinal");
                a.addState(new State(id, name != null ? name : id, isStart, isFinal));
            }
        }

        // Parse transitions
        List<Object> transArr = JsonUtil.getArray(map, "transitions");
        for (Object tObj : transArr) {
            if (tObj instanceof Map<?, ?> tMap) {
                Map<String, Object> tm = (Map<String, Object>) tMap;
                String src = JsonUtil.getString(tm, "sourceStateId");
                String tgt = JsonUtil.getString(tm, "targetStateId");
                String sym = JsonUtil.getString(tm, "symbol");
                a.addTransition(new Transition(src, tgt, sym));
            }
        }

        // Parse alphabet (may also be derived from transitions, but explicit is fine)
        List<Object> alphaArr = JsonUtil.getArray(map, "alphabet");
        for (Object aObj : alphaArr) {
            if (aObj instanceof String s) {
                a.addAlphabetSymbol(s);
            }
        }

        // Start state
        String startId = JsonUtil.getString(map, "startStateId");
        if (startId != null && !startId.equals("null")) {
            a.setStartStateId(startId);
        }

        // Final state IDs
        List<Object> finalArr = JsonUtil.getArray(map, "finalStateIds");
        for (Object fObj : finalArr) {
            if (fObj instanceof String s) {
                a.addFinalStateId(s);
            }
        }

        return a;
    }

    @Override
    public String toString() {
        return "Automaton{states=%d, transitions=%d, alphabet=%s, start=%s, finals=%s}"
                .formatted(states.size(), transitions.size(), alphabet, startStateId, finalStateIds);
    }
}
