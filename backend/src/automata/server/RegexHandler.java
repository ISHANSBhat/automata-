package automata.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import automata.engine.ThompsonsConstruction;
import automata.engine.StepLogger.ThompsonResult;
import automata.util.JsonUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * POST /api/regex
 *
 * Request:  { "regex": "(a|b)*abb" }
 * Response: { "automaton": {...}, "steps": [...] }
 */
public class RegexHandler implements HttpHandler {

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

            String regex = JsonUtil.getString(request, "regex");
            if (regex == null || regex.isBlank()) {
                sendJson(exchange, 400, JsonUtil.objectOf(
                        "error", JsonUtil.quoted("Missing or empty 'regex' in request body")));
                return;
            }

            ThompsonResult result = ThompsonsConstruction.build(regex);
            sendJson(exchange, 200, result.toJson());

        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, JsonUtil.objectOf(
                    "error", JsonUtil.quoted("Invalid regex: " + JsonUtil.escape(e.getMessage()))));
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
