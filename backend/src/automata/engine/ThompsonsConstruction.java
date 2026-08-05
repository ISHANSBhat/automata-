package automata.engine;

import automata.model.Automaton;
import automata.model.State;
import automata.model.Transition;
import automata.engine.StepLogger.ThompsonStep;
import automata.engine.StepLogger.ThompsonResult;

import java.util.*;

/**
 * Thompson's Construction: converts a Regular Expression to an ε-NFA.
 * Matches JS FALib.regexToNFA logic using a recursive-descent parser.
 */
public final class ThompsonsConstruction {

    private ThompsonsConstruction() {}

    // AST Nodes matching JS regexToNFA parser
    private sealed interface ASTNode permits ASTEps, ASTChar, ASTCat, ASTUnion, ASTStar, ASTPlus, ASTOpt {}
    private record ASTEps() implements ASTNode {}
    private record ASTChar(String ch) implements ASTNode {}
    private record ASTCat(List<ASTNode> nodes) implements ASTNode {}
    private record ASTUnion(ASTNode a, ASTNode b) implements ASTNode {}
    private record ASTStar(ASTNode a) implements ASTNode {}
    private record ASTPlus(ASTNode a) implements ASTNode {}
    private record ASTOpt(ASTNode a) implements ASTNode {}

    private record Fragment(String startId, String acceptId) {}

    /**
     * Build an ε-NFA from a regular expression string using recursive-descent parsing.
     */
    public static ThompsonResult build(String regexStr) {
        if (regexStr == null) {
            throw new IllegalArgumentException("Null regular expression");
        }
        String src = regexStr.trim();
        if (src.isEmpty()) {
            throw new IllegalArgumentException("Empty regular expression");
        }

        List<ThompsonStep> steps = new ArrayList<>();
        int stepNum = 1;

        // Recursive descent parser matching JS regexToNFA
        Parser parser = new Parser(src);
        ASTNode ast;
        try {
            ast = parser.parseUnion();
            if (parser.hasMore()) {
                throw new IllegalArgumentException("Trailing characters at position " + parser.pos);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        steps.add(new ThompsonStep(stepNum++, "parse", src, "Parsed AST for regex: " + src));

        // Build NFA from AST
        Automaton nfa = new Automaton();
        List<State> states = new ArrayList<>();
        List<Transition> transitions = new ArrayList<>();

        Builder builder = new Builder(states, transitions);
        Fragment res = builder.build(ast);

        // Mark start and final states
        for (State s : states) {
            boolean isStart = s.id().equals(res.startId);
            boolean isFinal = s.id().equals(res.acceptId);
            nfa.addState(new State(s.id(), s.name(), isStart, isFinal));
            if (isStart) nfa.setStartStateId(s.id());
            if (isFinal) nfa.addFinalStateId(s.id());
        }
        for (Transition t : transitions) {
            nfa.addTransition(t);
        }

        steps.add(new ThompsonStep(stepNum, "complete", "",
                "Construction complete. ε-NFA has " + nfa.getStates().size() + " states. " +
                        "Start: " + res.startId + ", Accept: " + res.acceptId));

        return new ThompsonResult(nfa, steps);
    }

    private static class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        boolean hasMore() {
            return pos < src.length();
        }

        char peek() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        char next() {
            return src.charAt(pos++);
        }

        ASTNode parseUnion() {
            ASTNode node = parseConcat();
            while (peek() == '|') {
                next();
                ASTNode rhs = parseConcat();
                node = new ASTUnion(node, rhs);
            }
            return node;
        }

        ASTNode parseConcat() {
            List<ASTNode> nodes = new ArrayList<>();
            while (pos < src.length() && peek() != ')' && peek() != '|') {
                nodes.add(parsePostfix());
            }
            if (nodes.isEmpty()) return new ASTEps();
            if (nodes.size() == 1) return nodes.get(0);
            return new ASTCat(nodes);
        }

        ASTNode parsePostfix() {
            ASTNode node = parseAtom();
            for (;;) {
                char ch = peek();
                if (ch == '*') { next(); node = new ASTStar(node); }
                else if (ch == '+') { next(); node = new ASTPlus(node); }
                else if (ch == '?') { next(); node = new ASTOpt(node); }
                else break;
            }
            return node;
        }

        ASTNode parseAtom() {
            if (!hasMore()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }
            char ch = next();
            if (ch == ')' || ch == '|' || ch == '*' || ch == '+' || ch == '?') {
                throw new IllegalArgumentException("Unexpected character '" + ch + "' at position " + pos);
            }
            if (ch == '(') {
                ASTNode n = parseUnion();
                if (!hasMore() || next() != ')') {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
                return n;
            }
            if (ch == 'ε') return new ASTEps();
            return new ASTChar(String.valueOf(ch));
        }
    }

    private static class Builder {
        private final List<State> states;
        private final List<Transition> transitions;

        Builder(List<State> states, List<Transition> transitions) {
            this.states = states;
            this.transitions = transitions;
        }

        String mk() {
            String id = "R" + states.size();
            String label = "q" + states.size();
            states.add(new State(id, label, false, false));
            return id;
        }

        void tr(String f, String t, String symbol) {
            transitions.add(new Transition(f, t, symbol));
        }

        Fragment build(ASTNode node) {
            return switch (node) {
                case ASTEps eps -> {
                    String s = mk(), e = mk();
                    tr(s, e, Transition.EPSILON);
                    yield new Fragment(s, e);
                }
                case ASTChar ch -> {
                    String s = mk(), e = mk();
                    tr(s, e, ch.ch());
                    yield new Fragment(s, e);
                }
                case ASTCat cat -> {
                    String first = null, prevEnd = null;
                    for (ASTNode n : cat.nodes()) {
                        Fragment r = build(n);
                        if (first == null) first = r.startId();
                        if (prevEnd != null) tr(prevEnd, r.startId(), Transition.EPSILON);
                        prevEnd = r.acceptId();
                    }
                    yield new Fragment(first, prevEnd);
                }
                case ASTUnion un -> {
                    Fragment ra = build(un.a());
                    Fragment rb = build(un.b());
                    String s = mk(), e = mk();
                    tr(s, ra.startId(), Transition.EPSILON);
                    tr(s, rb.startId(), Transition.EPSILON);
                    tr(ra.acceptId(), e, Transition.EPSILON);
                    tr(rb.acceptId(), e, Transition.EPSILON);
                    yield new Fragment(s, e);
                }
                case ASTStar st -> {
                    Fragment r = build(st.a());
                    String s = mk(), e = mk();
                    tr(s, r.startId(), Transition.EPSILON);
                    tr(s, e, Transition.EPSILON);
                    tr(r.acceptId(), r.startId(), Transition.EPSILON);
                    tr(r.acceptId(), e, Transition.EPSILON);
                    yield new Fragment(s, e);
                }
                case ASTPlus pl -> {
                    Fragment r = build(pl.a());
                    String s = mk(), e = mk();
                    tr(s, r.startId(), Transition.EPSILON);
                    tr(r.acceptId(), r.startId(), Transition.EPSILON);
                    tr(r.acceptId(), e, Transition.EPSILON);
                    yield new Fragment(s, e);
                }
                case ASTOpt opt -> {
                    Fragment r = build(opt.a());
                    String s = mk(), e = mk();
                    tr(s, r.startId(), Transition.EPSILON);
                    tr(s, e, Transition.EPSILON);
                    tr(r.acceptId(), e, Transition.EPSILON);
                    yield new Fragment(s, e);
                }
            };
        }
    }
}
