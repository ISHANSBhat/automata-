package automata.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Serves static files from the {@code frontend/} directory.
 * Resolves the frontend root relative to the working directory.
 */
public class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> MIME_TYPES = Map.of(
            ".html", "text/html; charset=UTF-8",
            ".css", "text/css; charset=UTF-8",
            ".js", "application/javascript; charset=UTF-8",
            ".json", "application/json; charset=UTF-8",
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".svg", "image/svg+xml",
            ".ico", "image/x-icon",
            ".woff2", "font/woff2"
    );

    private final Path frontendRoot;

    public StaticFileHandler() {
        // Resolve frontend/ relative to the current working directory
        this.frontendRoot = Path.of(System.getProperty("user.dir"), "frontend")
                .toAbsolutePath().normalize();
        System.out.println("Serving static files from: " + frontendRoot);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        String uriPath = exchange.getRequestURI().getPath();

        // Don't handle /api/ routes (they have their own handlers)
        if (uriPath.startsWith("/api/")) {
            sendError(exchange, 404, "Not Found");
            return;
        }

        // Default to index.html for bare root
        if ("/".equals(uriPath) || uriPath.isEmpty()) {
            uriPath = "/index.html";
        }

        // Resolve the file path, preventing directory traversal
        Path filePath = frontendRoot.resolve(uriPath.substring(1)).normalize();
        if (!filePath.startsWith(frontendRoot)) {
            sendError(exchange, 403, "Forbidden");
            return;
        }

        // If path points to a directory, try index.html inside it
        if (Files.isDirectory(filePath)) {
            filePath = filePath.resolve("index.html");
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            sendError(exchange, 404, "Not Found: " + uriPath);
            return;
        }

        // Determine MIME type
        String fileName = filePath.getFileName().toString();
        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.'))
                : "";
        String contentType = MIME_TYPES.getOrDefault(ext.toLowerCase(), "application/octet-stream");

        // Read and serve the file
        byte[] fileBytes = Files.readAllBytes(filePath);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] body = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
