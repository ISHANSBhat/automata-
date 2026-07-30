package automata.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import automata.model.Automaton;
import automata.engine.SimulationEngine;
import automata.engine.StepLogger.SimulationResult;
import automata.util.JsonUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * POST /api/simulate
 *
 * Request:  { "automaton": {...}, "input": "abc", "type": "DFA"|"NFA"|"ENFA" }
 * Response: { "accepted": true|false, "steps": [...] }
 */
public class SimulateHandler implements HttpHandler {

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

            // Extract fields
            Map<String, Object> automatonMap = JsonUtil.getObject(request, "automaton");
            if (automatonMap == null) {
                sendJson(exchange, 400, JsonUtil.objectOf(
                        "error", JsonUtil.quoted("Missing 'automaton' in request body")));
                return;
            }

            String input = JsonUtil.getString(request, "input");
            if (input == null) input = "";

            String type = JsonUtil.getString(request, "type");
            if (type == null) type = "DFA";

            // Build automaton from JSON
            Automaton automaton = Automaton.fromJsonMap(automatonMap);

            // Run simulation
            SimulationResult result = SimulationEngine.simulate(automaton, input, type);

            // Return result
            sendJson(exchange, 200, result.toJson());

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
