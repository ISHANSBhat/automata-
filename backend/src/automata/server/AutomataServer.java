package automata.server;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Main entry point for the Automata Maker backend.
 * Starts a lightweight HTTP server on port 8080 that serves:
 * <ul>
 *   <li>Static frontend files from the {@code frontend/} directory</li>
 *   <li>Three stateless computation API endpoints</li>
 * </ul>
 */
public class AutomataServer {

    public static void main(String[] args) throws IOException {
        String envPort = System.getenv("PORT");
        int port = (envPort != null && !envPort.isBlank()) ? Integer.parseInt(envPort) : 12000;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Static file serving (frontend)
        server.createContext("/", new StaticFileHandler());

        // Stateless computation endpoints
        server.createContext("/api/simulate", new SimulateHandler());
        server.createContext("/api/convert", new ConvertHandler());
        server.createContext("/api/regex", new RegexHandler());

        // Use a cached thread pool for concurrent request handling
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("""
                ╔══════════════════════════════════════════════╗
                ║          Automata Maker Server             ║
                ║                                              ║
                ║   Running on: http://localhost:%d          ║
                ║   Press Ctrl+C to stop                       ║
                ╚══════════════════════════════════════════════╝
                """.formatted(port));
    }
}
