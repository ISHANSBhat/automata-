package automata.engine;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Regex AST used internally by {@link StateElimination}.
 *
 * <p>A small sealed hierarchy representing regular expressions as a tree
 * rather than raw strings. Every factory method ({@link #union}, {@link #concat},
 * {@link #star}) eagerly simplifies the result so intermediate expressions
 * stay compact throughout the GNFA elimination loop.</p>
 */
public sealed interface RegexNode
        permits RegexNode.Empty, RegexNode.Eps, RegexNode.Lit,
                RegexNode.Union, RegexNode.Concat, RegexNode.Star {

    /** ∅ — the empty language (no strings accepted). */
    record Empty() implements RegexNode {}

    /** ε — the language containing only the empty string. */
    record Eps() implements RegexNode {}

    /** A single literal symbol. */
    record Lit(String symbol) implements RegexNode {}

    /** n-ary union (R1 | R2 | … | Rn), kept flattened and deduplicated. */
    record Union(List<RegexNode> operands) implements RegexNode {
        public Union { operands = List.copyOf(operands); }
    }

    /** n-ary concatenation (R1 · R2 · … · Rn), kept flattened. */
    record Concat(List<RegexNode> operands) implements RegexNode {
        public Concat { operands = List.copyOf(operands); }
    }

    /** Kleene star (R*). */
    record Star(RegexNode operand) implements RegexNode {}

    // =========================================================================
    // Factory methods — build + simplify eagerly
    // =========================================================================

    /** Build a simplified union of two regex nodes. */
    static RegexNode union(RegexNode a, RegexNode b) {
        List<RegexNode> ops = new ArrayList<>();
        ops.add(a);
        ops.add(b);
        return simplify(new Union(ops));
    }

    /** Build a simplified concatenation of two regex nodes. */
    static RegexNode concat(RegexNode a, RegexNode b) {
        List<RegexNode> ops = new ArrayList<>();
        ops.add(a);
        ops.add(b);
        return simplify(new Concat(ops));
    }

    /** Build a simplified Kleene star. */
    static RegexNode star(RegexNode a) {
        return simplify(new Star(a));
    }

    // =========================================================================
    // Simplification — apply rules bottom-up to a fixpoint
    // =========================================================================

    /**
     * Apply all simplification rules repeatedly until nothing changes.
     */
    static RegexNode simplify(RegexNode node) {
        // First, simplify children bottom-up
        RegexNode current = simplifyChildren(node);
        // Then apply top-level rules to a fixpoint
        RegexNode prev;
        do {
            prev = current;
            current = applyRules(current);
        } while (!current.equals(prev));
        return current;
    }

    /** Recursively simplify all children first (bottom-up). */
    private static RegexNode simplifyChildren(RegexNode node) {
        return switch (node) {
            case Empty e -> e;
            case Eps e -> e;
            case Lit l -> l;
            case Star s -> new Star(simplify(s.operand()));
            case Union u -> {
                List<RegexNode> simplified = u.operands().stream()
                        .map(RegexNode::simplify)
                        .collect(Collectors.toCollection(ArrayList::new));
                yield new Union(simplified);
            }
            case Concat c -> {
                List<RegexNode> simplified = c.operands().stream()
                        .map(RegexNode::simplify)
                        .collect(Collectors.toCollection(ArrayList::new));
                yield new Concat(simplified);
            }
        };
    }

    /**
     * Apply one pass of all simplification rules. Returns the (possibly changed) node.
     */
    private static RegexNode applyRules(RegexNode node) {
        return switch (node) {
            case Empty e -> e;
            case Eps e -> e;
            case Lit l -> l;
            case Star s -> simplifyStar(s);
            case Union u -> simplifyUnion(u);
            case Concat c -> simplifyConcat(c);
        };
    }

    // --- Rule 3: Star collapse ---

    private static RegexNode simplifyStar(Star s) {
        RegexNode inner = s.operand();
        // Star(Empty) → Eps
        if (inner instanceof Empty) return new Eps();
        // Star(Eps) → Eps
        if (inner instanceof Eps) return new Eps();
        // Star(Star(x)) → Star(x)
        if (inner instanceof Star) return inner;
        // Star(Union containing Eps) → Star(Union without Eps)
        // e.g. (ε|a)* = a*
        if (inner instanceof Union u) {
            List<RegexNode> withoutEps = u.operands().stream()
                    .filter(op -> !(op instanceof Eps))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (withoutEps.size() < u.operands().size()) {
                // Had epsilon in the union
                RegexNode reduced;
                if (withoutEps.isEmpty()) return new Eps();
                else if (withoutEps.size() == 1) reduced = withoutEps.get(0);
                else reduced = new Union(withoutEps);
                return new Star(reduced);
            }
        }
        return s;
    }

    // --- Rules 4, 6, 7: Union cleanup, common-factor, star-unfold ---

    private static RegexNode simplifyUnion(Union u) {
        List<RegexNode> ops = new ArrayList<>(u.operands());

        // Rule 4a: flatten nested unions
        ops = flattenUnion(ops);

        // Rule 1 (Empty identity for union): drop Empty operands
        ops.removeIf(op -> op instanceof Empty);

        // Rule 4b: dedup by structural equality
        ops = dedup(ops);

        // Rule 7: star-unfold recognition
        // If operands include Eps and a Concat [X, Star(X)], replace with Star(X)
        ops = starUnfold(ops);

        // Rule 6: common-factor extraction (prefix and suffix)
        ops = commonFactorExtraction(ops);

        // Final cleanup
        if (ops.isEmpty()) return new Empty();
        if (ops.size() == 1) return ops.get(0);
        return new Union(ops);
    }

    private static List<RegexNode> flattenUnion(List<RegexNode> ops) {
        List<RegexNode> result = new ArrayList<>();
        for (RegexNode op : ops) {
            if (op instanceof Union nested) {
                result.addAll(nested.operands());
            } else {
                result.add(op);
            }
        }
        return result;
    }

    private static List<RegexNode> dedup(List<RegexNode> ops) {
        List<RegexNode> result = new ArrayList<>();
        Set<RegexNode> seen = new LinkedHashSet<>();
        for (RegexNode op : ops) {
            if (seen.add(op)) {
                result.add(op);
            }
        }
        return result;
    }

    /**
     * Star-unfold: if the union contains ε and Concat(X, Star(X)) for some X,
     * replace both with Star(X).
     */
    private static List<RegexNode> starUnfold(List<RegexNode> ops) {
        boolean hasEps = ops.stream().anyMatch(op -> op instanceof Eps);
        if (!hasEps) return ops;

        for (int i = 0; i < ops.size(); i++) {
            RegexNode op = ops.get(i);
            List<RegexNode> concatOps = toConcatList(op);
            if (concatOps == null || concatOps.size() < 2) continue;

            // Check if last element is Star(X) and prefix is X
            RegexNode last = concatOps.get(concatOps.size() - 1);
            if (last instanceof Star starNode) {
                RegexNode x = starNode.operand();
                List<RegexNode> prefix = concatOps.subList(0, concatOps.size() - 1);
                RegexNode prefixNode = (prefix.size() == 1) ? prefix.get(0) : new Concat(prefix);
                if (prefixNode.equals(x)) {
                    // Match! Replace Eps + Concat(X, Star(X)) with Star(X)
                    List<RegexNode> result = new ArrayList<>();
                    for (int j = 0; j < ops.size(); j++) {
                        if (j == i) {
                            result.add(new Star(x));
                        } else if (ops.get(j) instanceof Eps) {
                            // skip the eps we consumed
                        } else {
                            result.add(ops.get(j));
                        }
                    }
                    return result;
                }
            }
        }
        return ops;
    }

    /**
     * Common-factor extraction: for any two union operands that share a common
     * leading or trailing sequence of concat sub-terms, factor it out.
     *
     * E.g. Union(ab, aXb) → Concat(a, Union(b, Xb))
     */
    private static List<RegexNode> commonFactorExtraction(List<RegexNode> ops) {
        if (ops.size() < 2) return ops;

        // Try prefix factoring for every pair
        for (int i = 0; i < ops.size(); i++) {
            for (int j = i + 1; j < ops.size(); j++) {
                List<RegexNode> a = toConcatListSafe(ops.get(i));
                List<RegexNode> b = toConcatListSafe(ops.get(j));

                // Common prefix
                int prefixLen = commonPrefixLength(a, b);
                if (prefixLen > 0) {
                    List<RegexNode> prefix = a.subList(0, prefixLen);
                    List<RegexNode> aRest = a.subList(prefixLen, a.size());
                    List<RegexNode> bRest = b.subList(prefixLen, b.size());

                    RegexNode aRestNode = concatFromList(aRest);
                    RegexNode bRestNode = concatFromList(bRest);
                    RegexNode combined = simplify(union(aRestNode, bRestNode));

                    List<RegexNode> factored = new ArrayList<>(prefix);
                    factored.add(combined);
                    RegexNode factoredNode = concatFromList(factored);

                    List<RegexNode> result = new ArrayList<>();
                    for (int k = 0; k < ops.size(); k++) {
                        if (k == i) result.add(factoredNode);
                        else if (k == j) { /* skip, merged into i */ }
                        else result.add(ops.get(k));
                    }
                    return result;
                }

                // Common suffix
                int suffixLen = commonSuffixLength(a, b);
                if (suffixLen > 0) {
                    List<RegexNode> suffix = a.subList(a.size() - suffixLen, a.size());
                    List<RegexNode> aRest = a.subList(0, a.size() - suffixLen);
                    List<RegexNode> bRest = b.subList(0, b.size() - suffixLen);

                    RegexNode aRestNode = concatFromList(aRest);
                    RegexNode bRestNode = concatFromList(bRest);
                    RegexNode combined = simplify(union(aRestNode, bRestNode));

                    List<RegexNode> factored = new ArrayList<>();
                    factored.add(combined);
                    factored.addAll(suffix);
                    RegexNode factoredNode = concatFromList(factored);

                    List<RegexNode> result = new ArrayList<>();
                    for (int k = 0; k < ops.size(); k++) {
                        if (k == i) result.add(factoredNode);
                        else if (k == j) { /* skip */ }
                        else result.add(ops.get(k));
                    }
                    return result;
                }
            }
        }
        return ops;
    }

    // --- Rule 2, 5, 1: Concat simplification ---

    private static RegexNode simplifyConcat(Concat c) {
        List<RegexNode> ops = new ArrayList<>(c.operands());

        // Rule 5: flatten nested Concats
        ops = flattenConcat(ops);

        // Rule 1: Empty absorption — if any operand is Empty, whole concat is Empty
        for (RegexNode op : ops) {
            if (op instanceof Empty) return new Empty();
        }

        // Rule 2: Epsilon identity — drop Eps operands
        ops.removeIf(op -> op instanceof Eps);

        // Empty concat → Eps
        if (ops.isEmpty()) return new Eps();
        if (ops.size() == 1) return ops.get(0);
        return new Concat(ops);
    }

    private static List<RegexNode> flattenConcat(List<RegexNode> ops) {
        List<RegexNode> result = new ArrayList<>();
        for (RegexNode op : ops) {
            if (op instanceof Concat nested) {
                result.addAll(nested.operands());
            } else {
                result.add(op);
            }
        }
        return result;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Convert a node to its concat operand list, or null if not a Concat. */
    private static List<RegexNode> toConcatList(RegexNode node) {
        if (node instanceof Concat c) return new ArrayList<>(c.operands());
        return null;
    }

    /** Convert a node to its concat operand list; non-Concat nodes become a singleton list. */
    private static List<RegexNode> toConcatListSafe(RegexNode node) {
        if (node instanceof Concat c) return new ArrayList<>(c.operands());
        return new ArrayList<>(List.of(node));
    }

    /** Build a Concat (or simpler) node from a list of operands. */
    private static RegexNode concatFromList(List<RegexNode> ops) {
        if (ops.isEmpty()) return new Eps();
        if (ops.size() == 1) return ops.get(0);
        return new Concat(ops);
    }

    private static int commonPrefixLength(List<RegexNode> a, List<RegexNode> b) {
        int len = 0;
        int min = Math.min(a.size(), b.size());
        while (len < min && a.get(len).equals(b.get(len))) {
            len++;
        }
        return len;
    }

    private static int commonSuffixLength(List<RegexNode> a, List<RegexNode> b) {
        int len = 0;
        int ai = a.size() - 1, bi = b.size() - 1;
        while (ai >= 0 && bi >= 0 && a.get(ai).equals(b.get(bi))) {
            len++;
            ai--;
            bi--;
        }
        return len;
    }

    // =========================================================================
    // Rendering — AST → display string
    // =========================================================================

    // Precedence: Star (3) > Concat (2) > Union (1)
    int PREC_UNION  = 1;
    int PREC_CONCAT = 2;
    int PREC_STAR   = 3;
    int PREC_ATOM   = 4;  // literals, ε, ∅, parenthesized

    /**
     * Render a RegexNode to a display string using minimal parenthesization
     * based on operator precedence.
     */
    static String render(RegexNode node) {
        return renderNode(node);
    }

    private static String renderNode(RegexNode node) {
        return switch (node) {
            case Empty e -> "∅";
            case Eps e -> "ε";
            case Lit l -> l.symbol();
            case Star s -> renderWithPrec(s.operand(), PREC_STAR) + "*";
            case Union u -> {
                yield u.operands().stream()
                        .map(RegexNode::renderNode)
                        .collect(Collectors.joining("|"));
            }
            case Concat c -> {
                yield c.operands().stream()
                        .map(op -> renderWithPrec(op, PREC_CONCAT))
                        .collect(Collectors.joining());
            }
        };
    }

    /**
     * Render a child node, wrapping in parentheses if its precedence is lower
     * than the required context precedence.
     */
    private static String renderWithPrec(RegexNode node, int contextPrec) {
        int nodePrec = precedenceOf(node);
        if (nodePrec < contextPrec) {
            return "(" + renderNode(node) + ")";
        }
        return renderNode(node);
    }

    private static int precedenceOf(RegexNode node) {
        return switch (node) {
            case Empty e -> PREC_ATOM;
            case Eps e -> PREC_ATOM;
            case Lit l -> PREC_ATOM;
            case Star s -> PREC_STAR;
            case Concat c -> PREC_CONCAT;
            case Union u -> PREC_UNION;
        };
    }
}
