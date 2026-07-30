package automata.engine;

import automata.util.JsonUtil;

import java.util.List;
import java.util.Set;

/**
 * Data structures for step-by-step algorithm logging.
 * Used by SimulationEngine, SubsetConstruction, and ThompsonsConstruction.
 */
public final class StepLogger {

    private StepLogger() {}

    // =========================================================================
    // Simulation step
    // =========================================================================

    /**
     * One step in a simulation trace.
     * @param stepNumber   1-based step index
     * @param symbolRead   the symbol consumed at this step (empty string for initial state)
     * @param currentStateIds  the set of active state IDs after this step
     * @param description  human-readable description of what happened
     */
    public record StepDetail(
            int stepNumber,
            String symbolRead,
            Set<String> currentStateIds,
            String description
    ) {
        public String toJson() {
            List<String> stateJsons = currentStateIds.stream()
                    .map(JsonUtil::quoted)
                    .toList();

            return JsonUtil.objectOf(
                    "stepNumber", String.valueOf(stepNumber),
                    "symbolRead", JsonUtil.quoted(symbolRead),
                    "currentStateIds", JsonUtil.array(stateJsons),
                    "description", JsonUtil.quoted(description)
            );
        }
    }

    // =========================================================================
    // Simulation result
    // =========================================================================

    /**
     * Complete result of a string simulation.
     */
    public record SimulationResult(boolean accepted, List<StepDetail> steps) {
        public String toJson() {
            List<String> stepJsons = steps.stream()
                    .map(StepDetail::toJson)
                    .toList();

            return JsonUtil.objectOf(
                    "accepted", String.valueOf(accepted),
                    "steps", JsonUtil.array(stepJsons)
            );
        }
    }

    // =========================================================================
    // Conversion step (for Subset Construction)
    // =========================================================================

    /**
     * One step in an NFA→DFA subset construction.
     */
    public record ConversionStep(
            int stepNumber,
            Set<String> nfaStateSet,
            String dfaStateName,
            String description
    ) {
        public String toJson() {
            List<String> nfaJsons = nfaStateSet.stream()
                    .map(JsonUtil::quoted)
                    .toList();

            return JsonUtil.objectOf(
                    "stepNumber", String.valueOf(stepNumber),
                    "nfaStateSet", JsonUtil.array(nfaJsons),
                    "dfaStateName", JsonUtil.quoted(dfaStateName),
                    "description", JsonUtil.quoted(description)
            );
        }
    }

    /**
     * Complete result of a conversion.
     */
    public record ConversionResult(
            automata.model.Automaton resultDFA,
            List<ConversionStep> steps
    ) {
        public String toJson() {
            List<String> stepJsons = steps.stream()
                    .map(ConversionStep::toJson)
                    .toList();

            return JsonUtil.objectOf(
                    "automaton", resultDFA.toJson(),
                    "steps", JsonUtil.array(stepJsons)
            );
        }
    }

    // =========================================================================
    // Thompson's construction step
    // =========================================================================

    /**
     * One step in Thompson's regex→ε-NFA construction.
     */
    public record ThompsonStep(
            int stepNumber,
            String operation,
            String operand,
            String description
    ) {
        public String toJson() {
            return JsonUtil.objectOf(
                    "stepNumber", String.valueOf(stepNumber),
                    "operation", JsonUtil.quoted(operation),
                    "operand", JsonUtil.quoted(operand),
                    "description", JsonUtil.quoted(description)
            );
        }
    }

    /**
     * Complete result of Thompson's construction.
     */
    public record ThompsonResult(
            automata.model.Automaton automaton,
            List<ThompsonStep> steps
    ) {
        public String toJson() {
            List<String> stepJsons = steps.stream()
                    .map(ThompsonStep::toJson)
                    .toList();

            return JsonUtil.objectOf(
                    "automaton", automaton.toJson(),
                    "steps", JsonUtil.array(stepJsons)
            );
        }
    }
}
