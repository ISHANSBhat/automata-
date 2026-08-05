package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.ThompsonStep;
import automata.util.JsonUtil;

import java.util.*;

/**
 * State Elimination algorithm matching JS FALib.dfaToRegex logic.
 * Performs pure string-based regex algebra.
 */
public final class StateElimination {

    private StateElimination() {}

    public record RegexResult(String regex, List<ThompsonStep> steps) {
        public String toJson() {
            List<String> stepJsons = steps.stream()
                    .map(ThompsonStep::toJson)
                    .toList();
            return JsonUtil.objectOf(
                    "regex", regex == null ? "null" : JsonUtil.quoted(regex),
                    "steps", JsonUtil.array(stepJsons)
            );
        }
    }

    @FunctionalInterface
    private interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    public static RegexResult toRegex(Automaton a) {
        Automaton src = a;
        if (src.hasEpsilonTransitions()) {
            src = SimulationEngine.removeEpsilon(a);
        }

        String startId = src.getStartStateId();
        if (startId == null) {
            return new RegexResult(null, List.of());
        }

        Map<String, String> R = new HashMap<>();

        java.util.function.BiFunction<String, String, String> key = (p, q) -> p + "\u0000" + q;
        TriConsumer<String, String, String> set = (p, q, v) -> {
            if ("∅".equals(v)) R.remove(key.apply(p, q));
            else R.put(key.apply(p, q), v);
        };
        java.util.function.BiFunction<String, String, String> get = (p, q) -> R.getOrDefault(key.apply(p, q), "∅");

        for (State s : src.getStates()) {
            R.put(key.apply(s.id(), s.id()), Transition.EPSILON);
        }

        for (Transition t : src.getTransitions()) {
            set.accept(t.sourceStateId(), t.targetStateId(), union(get.apply(t.sourceStateId(), t.targetStateId()), t.symbol()));
        }

        String ns = "⟳NS";
        String nf = "⟳NF";
        set.accept(ns, startId, Transition.EPSILON);

        for (State s : src.getStates()) {
            if (s.isFinal()) {
                set.accept(s.id(), nf, Transition.EPSILON);
            }
        }

        List<String> order = src.getStates().stream().map(State::id).toList();
        List<ThompsonStep> steps = new ArrayList<>();
        int stepNum = 1;

        for (String q : order) {
            Set<String> incoming = new HashSet<>();
            Set<String> outgoing = new HashSet<>();

            for (Map.Entry<String, String> entry : R.entrySet()) {
                String v = entry.getValue();
                if ("∅".equals(v)) continue;
                String[] parts = entry.getKey().split("\u0000");
                String p = parts[0];
                String qq = parts[1];

                if (qq.equals(q) && !p.equals(q)) incoming.add(p);
                if (p.equals(q) && !qq.equals(q)) outgoing.add(qq);
            }

            String loopStar = star(get.apply(q, q));

            for (String p : incoming) {
                for (String r : outgoing) {
                    set.accept(p, r, union(get.apply(p, r), concat(concat(get.apply(p, q), loopStar), get.apply(q, r))));
                }
            }

            List<String> keysToRemove = new ArrayList<>();
            for (String k : R.keySet()) {
                String[] parts = k.split("\u0000");
                String p = parts[0];
                String qq = parts[1];
                if (p.equals(q) || qq.equals(q)) {
                    keysToRemove.add(k);
                }
            }
            for (String k : keysToRemove) {
                R.remove(k);
            }

            steps.add(new ThompsonStep(stepNum++, "eliminate", q, "Eliminated state " + q));
        }

        String regex = get.apply(ns, nf);
        String finalRegex = "∅".equals(regex) ? null : regex;

        return new RegexResult(finalRegex, steps);
    }

    // --- Pure String-based Regex Helper Functions matching JS FALib ---

    public static List<String> splitAlts(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
            else if (ch == '|' && depth == 0) {
                out.add(cur.toString());
                cur = new StringBuilder();
                continue;
            }
            cur.append(ch);
        }
        out.add(cur.toString());
        return out.stream().filter(a -> !a.isEmpty()).toList();
    }

    public static String union(String A, String B) {
        if ("∅".equals(A)) return B;
        if ("∅".equals(B)) return A;
        if (A.equals(B)) return A;

        List<String> alts = new ArrayList<>();
        for (String x : List.of(A, B)) {
            boolean wrapped = x.startsWith("(") && x.endsWith(")") && x.length() > 2;
            String inner = wrapped ? x.substring(1, x.length() - 1) : null;
            if (inner != null && splitAlts(inner).size() > 1) {
                alts.addAll(splitAlts(inner));
            } else {
                alts.add(x);
            }
        }

        Set<String> uniqueSet = new LinkedHashSet<>(alts);
        List<String> unique = new ArrayList<>(uniqueSet);
        if (unique.size() == 1) return unique.get(0);
        return "(" + String.join("|", unique) + ")";
    }

    public static String concat(String A, String B) {
        if ("∅".equals(A) || "∅".equals(B)) return "∅";
        if (Transition.EPSILON.equals(A)) return B;
        if (Transition.EPSILON.equals(B)) return A;
        return wrap(A) + wrap(B);
    }

    public static String wrap(String s) {
        if (s.length() == 1) return s;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
            else if (ch == '|' && depth == 0) return "(" + s + ")";
        }
        return s;
    }

    public static String star(String s) {
        if ("∅".equals(s)) return "∅";
        if (Transition.EPSILON.equals(s)) return Transition.EPSILON;
        String inner = s;
        if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
            String body = s.substring(1, s.length() - 1);
            List<String> alts = splitAlts(body);
            if (alts.size() > 1) {
                List<String> noEps = alts.stream().filter(a -> !Transition.EPSILON.equals(a)).toList();
                if (noEps.size() == alts.size()) inner = body;
                else if (noEps.size() == 1) inner = noEps.get(0);
                else inner = "(" + String.join("|", noEps) + ")";
            }
        }
        return "(" + inner + ")*";
    }
}
