package automata.model;

import java.util.Objects;

/**
 * Represents a directed transition between two states on a given symbol.
 * The symbol "ε" denotes an epsilon (empty) transition.
 * Uses the default record equals/hashCode (all three components).
 */
public record Transition(String sourceStateId, String targetStateId, String symbol) {

    /** The canonical string used for epsilon transitions. */
    public static final String EPSILON = "ε";

    public Transition {
        Objects.requireNonNull(sourceStateId, "sourceStateId must not be null");
        Objects.requireNonNull(targetStateId, "targetStateId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
    }

    /** Returns {@code true} if this is an epsilon transition. */
    public boolean isEpsilon() {
        return EPSILON.equals(symbol);
    }

    // --- JSON serialization ----------------------------------------------------

    public String toJson() {
        return """
               {"sourceStateId":"%s","targetStateId":"%s","symbol":"%s"}\
               """.formatted(
                escapeJson(sourceStateId),
                escapeJson(targetStateId),
                escapeJson(symbol)
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
