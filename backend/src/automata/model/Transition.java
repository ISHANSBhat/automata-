package automata.model;

import java.util.Objects;

/**
 * Represents a directed transition between two states on a given symbol.
 * The symbol "ε" denotes an epsilon (empty) transition.
 */
public record Transition(String id, String sourceStateId, String targetStateId, String symbol) {

    /** The canonical string used for epsilon transitions. */
    public static final String EPSILON = "ε";

    public Transition {
        Objects.requireNonNull(sourceStateId, "sourceStateId must not be null");
        Objects.requireNonNull(targetStateId, "targetStateId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (id == null || id.isBlank()) {
            id = "t_" + sourceStateId + "_" + symbol + "_" + targetStateId;
        }
    }

    /** Constructor without explicit id (auto-generates systematic id). */
    public Transition(String sourceStateId, String targetStateId, String symbol) {
        this("t_" + sourceStateId + "_" + symbol + "_" + targetStateId, sourceStateId, targetStateId, symbol);
    }

    /** Alias for sourceStateId (for JS compatibility). */
    public String from() {
        return sourceStateId;
    }

    /** Alias for targetStateId (for JS compatibility). */
    public String to() {
        return targetStateId;
    }

    /** Returns {@code true} if this is an epsilon transition. */
    public boolean isEpsilon() {
        return EPSILON.equals(symbol);
    }

    /** Returns a systematic string representation of the transition. */
    public String toFormattedString() {
        return "%s --%s--> %s".formatted(sourceStateId, symbol, targetStateId);
    }

    // --- JSON serialization ----------------------------------------------------

    public String toJson() {
        return """
               {"id":"%s","from":"%s","to":"%s","sourceStateId":"%s","targetStateId":"%s","symbol":"%s"}\
               """.formatted(
                escapeJson(id),
                escapeJson(sourceStateId),
                escapeJson(targetStateId),
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
