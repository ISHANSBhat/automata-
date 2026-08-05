package automata.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import automata.model.Automaton;
import automata.engine.SubsetConstruction;
import automata.engine.StateElimination;
import automata.engine.DFAMinimization;
import automata.engine.StepLogger.ConversionResult;
import automata.engine.StepLogger.ConversionStep;
import automata.util.JsonUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * POST /api/convert
 *
 * Request:  { "automaton": {...}, "type": "NFA_TO_DFA"|"ENFA_TO_DFA"|"DFA_TO_REGEX"|"MINIMIZE_DFA"|"NFA_TO_MINIMIZED_DFA" }
 * Response: { "automaton": {...}, "steps": [...] }
 *       or: { "regex": "...", "steps": [...] }   (for DFA_TO_REGEX)
 */
public class ConvertHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, JsonUtil.objectOf(
                    "error", JsonUtil.quoted("Method Not Allowed")));
            return;
        }

        try {
            String body = readBody(exchange);
            Map<String, Object> request = JsonUtil.parseObject(body);

            Map<String, Object> automatonMap = JsonUtil.getObject(request, "automaton");
            if (automatonMap == null) {
                sendJson(exchange, 400, JsonUtil.objectOf(
                        "error", JsonUtil.quoted("Missing 'automaton' in request body")));
                return;
            }

            String type = JsonUtil.getString(request, "type");
            if (type == null) type = "NFA_TO_DFA";

            Automaton automaton = Automaton.fromJsonMap(automatonMap);

            if ("DFA_TO_REGEX".equalsIgnoreCase(type)) {
                // State elimination: DFA/NFA → Regex
                StateElimination.RegexResult result = StateElimination.toRegex(automaton);
                sendJson(exchange, 200, result.toJson());
            } else if ("MINIMIZE_DFA".equalsIgnoreCase(type) || "DFA_MINIMIZE".equalsIgnoreCase(type) || "MINIMIZE".equalsIgnoreCase(type)) {
                // Minimize DFA
                Automaton minimized = DFAMinimization.minimize(automaton);
                if (minimized == null) {
                    sendJson(exchange, 400, JsonUtil.objectOf(
                            "error", JsonUtil.quoted("Automaton is not a valid DFA for minimization")));
                    return;
                }
                List<ConversionStep> steps = List.of(
                        new ConversionStep(1, java.util.Set.of(), "", "DFA Minimization complete: " + automaton.getStates().size() + " → " + minimized.getStates().size() + " states")
                );
                sendJson(exchange, 200, new ConversionResult(minimized, steps).toJson());
            } else if ("NFA_TO_MINIMIZED_DFA".equalsIgnoreCase(type)) {
                // Subset construction + minimization
                ConversionResult result = SubsetConstruction.convert(automaton, type);
                Automaton minimized = DFAMinimization.minimize(result.resultDFA());

                List<ConversionStep> allSteps = new ArrayList<>(result.steps());
                allSteps.add(new ConversionStep(
                        allSteps.size() + 1,
                        java.util.Set.of(),
                        "",
                        "Minimized DFA: " + result.resultDFA().getStates().size() +
                                " → " + (minimized != null ? minimized.getStates().size() : result.resultDFA().getStates().size()) + " states"));

                ConversionResult minimizedResult = new ConversionResult(minimized != null ? minimized : result.resultDFA(), allSteps);
                sendJson(exchange, 200, minimizedResult.toJson());
            } else {
                // Standard NFA/ε-NFA → DFA subset construction
                ConversionResult result = SubsetConstruction.convert(automaton, type);
                sendJson(exchange, 200, result.toJson());
            }

        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, JsonUtil.objectOf(
                    "error", JsonUtil.quoted("Bad request: " + JsonUtil.escape(e.getMessage()))));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, JsonUtil.objectOf(
                    "error", JsonUtil.quoted("Internal server error: " + JsonUtil.escape(e.getMessage()))));
        }
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
