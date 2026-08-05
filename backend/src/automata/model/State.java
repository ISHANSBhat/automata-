package automata.model;

import java.util.Objects;

/**
 * Represents a single state in a finite automaton.
 * Identity is based solely on {@code id} — two State records with the same id
 * but different isStart/isFinal flags are considered equal for Set membership.
 */
public record State(String id, String name, double x, double y, boolean isStart, boolean isFinal) {

    public State {
        Objects.requireNonNull(id, "State id must not be null");
        if (name == null) {
            name = id;
        }
    }

    /** Constructor without explicit x,y coordinates (defaults to 0,0). */
    public State(String id, String name, boolean isStart, boolean isFinal) {
        this(id, name, 0.0, 0.0, isStart, isFinal);
    }

    /** Convenience constructor: name defaults to id, x/y default to 0. */
    public State(String id, boolean isStart, boolean isFinal) {
        this(id, id, 0.0, 0.0, isStart, isFinal);
    }

    // --- Identity by id only ---------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // --- JSON serialization ----------------------------------------------------

    public String toJson() {
        return """
               {"id":"%s","name":"%s","label":"%s","x":%.1f,"y":%.1f,"isStart":%b,"isFinal":%b}\
               """.formatted(
                escapeJson(id),
                escapeJson(name),
                escapeJson(name),
                x,
                y,
                isStart,
                isFinal
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
