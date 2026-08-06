package comp489.a1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ============================================================================
 * ASSIGNMENT 1 - STARTER SCAFFOLD (proxy server)   ***implement the TODOs***
 * ----------------------------------------------------------------------------
 * Do NOT post completed assignment code to the Unit 4 Discussion.
 * ============================================================================
 *
 * The proxy is the core piece: a special HTTP server holding TWO socket
 * connections. It listens for the CLIENT on one socket and forwards the request
 * to the target WEB SERVER on the other, then relays the response back:
 *
 *      Client  <-->  [ Proxy ]  <-->  Web Server
 *
 * Because it must send and receive at the same time, each direction of the
 * relay is pumped on its own THREAD (see the relay() helper and the pump
 * Runnable below). This scaffold sets up the accept loop and the two-way pump
 * skeleton; you implement how the target host/port is chosen and any request
 * rewriting your assignment requires.
 */
public class ProxyServer {

    public static void main(String[] args) {
        int listenPort = (args.length > 0) ? Integer.parseInt(args[0]) : 8888;

        try (ServerSocket listen = new ServerSocket(listenPort)) {
            System.out.println("ProxyServer listening on port " + listenPort + " ... (Ctrl+C to stop)");

            while (true) {
                Socket client = listen.accept();
                // Hand each client to its own thread so the proxy stays responsive.
                new Thread(() -> serviceClient(client)).start();
            }
        } catch (IOException e) {
            System.out.println("Proxy could not listen on port " + listenPort + ": " + e.getMessage());
        }
    }

    private static void serviceClient(Socket client) {
        // TODO (assignment):
        //   1. read the client's request and determine the TARGET host + port
        //      (parse the requested URL, or an initial "CONNECT host:port" line).
        //   2. open a socket to that target web server:
        //          Socket target = new Socket(targetHost, targetPort);
        //   3. relay both directions concurrently (client->target, target->client).
        //
        // The example below shows the two-way relay structure; wire it to your
        // real 'target' socket once you have parsed the destination.

        String targetHost = "example.com";   // TODO: derive from the client request
        int targetPort = 80;

        try (client;
             Socket target = new Socket(targetHost, targetPort)) {

            // Pump client -> target on this thread's own worker...
            Thread c2t = new Thread(() -> relay(safeIn(client), safeOut(target), "client->target"));
            // ...and target -> client on another.
            Thread t2c = new Thread(() -> relay(safeIn(target), safeOut(client), "target->client"));

            c2t.start();
            t2c.start();
            c2t.join();
            t2c.join();
        } catch (IOException | InterruptedException e) {
            System.out.println("Proxy relay ended: " + e.getMessage());
        }
    }

    /** Copy bytes from 'in' to 'out' until the source is exhausted. */
    private static void relay(InputStream in, OutputStream out, String label) {
        if (in == null || out == null) return;
        byte[] buffer = new byte[4096];
        try {
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // Connection closed by one side - normal end of relay.
        }
        System.out.println(label + " finished.");
    }

    private static InputStream safeIn(Socket s) {
        try { return s.getInputStream(); } catch (IOException e) { return null; }
    }

    private static OutputStream safeOut(Socket s) {
        try { return s.getOutputStream(); } catch (IOException e) { return null; }
    }
}
