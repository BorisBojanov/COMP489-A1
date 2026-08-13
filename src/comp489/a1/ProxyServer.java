package comp489.a1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/*
 * ============================================================================
 *  Program : ProxyServer
 *  Course  : COMP 489 - Assignment 1
 *  Description:
 *      An HTTP proxy. It listens for a client on one socket, works out which
 *      web server the client is really trying to reach, opens a second socket
 *      to that server, forwards the request, and relays the reply back:
 *
 *          Client  <-->  [ ProxyServer ]  <-->  Web Server
 *
 *      Each accepted client is handled on its own thread. Within a client,
 *      the two directions of the relay (client->server and server->client)
 *      each run on their own thread so data can flow both ways at once.
 *
 *  Inputs  : args[0] = listen port (optional, default 8888)
 *  Outputs : relays bytes between client and target; logs to stdout
 * ============================================================================
 */
public class ProxyServer {

    private static final int DEFAULT_LISTEN_PORT = 8888;

    /*
     * main -- entry point. Opens the listening socket and accepts clients
     *         forever, handing each one to its own thread.
     * called by : the JVM
     * calls     : serviceClient
     */
    public static void main(String[] args) {
        int listenPort = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_LISTEN_PORT;

        try (ServerSocket listen = new ServerSocket(listenPort)) {
            System.out.println("ProxyServer listening on port " + listenPort + " ... (Ctrl+C to stop)");
            while (true) {
                Socket client = listen.accept();
                // one thread per client so the proxy stays responsive
                new Thread(() -> serviceClient(client)).start();
            }
        } catch (IOException e) {
            System.out.println("Proxy could not listen on port " + listenPort + ": " + e.getMessage());
        }
    }

    /*
     * serviceClient -- handle a single client connection end to end.
     * called by : main (on a worker thread)
     * calls     : readRequestHead, parseTargetHost, parseTargetPort, relay
     */
    private static void serviceClient(Socket client) {
        try (client) {
            // 1. Read the request line + headers from the client.
            //    Keep the exact text so we can forward it to the target.
            String requestHead = readRequestHead(client.getInputStream());
            if (requestHead == null || requestHead.isEmpty()) {
                return; // client sent nothing usable
            }
            System.out.println("--- REQUEST HEAD ---\n" + requestHead);

            // 2. Work out where the client actually wants to go.
            String targetHost = parseTargetHost(requestHead);
            int targetPort = parseTargetPort(requestHead);
            if (targetHost == null) {
                return; // could not determine a destination
            }
            System.out.println("Target: " + targetHost + ":" + targetPort);

            // 3. Connect to the real server and forward the request, then relay.
            try (Socket target = new Socket(targetHost, targetPort)) {

                // 4. Send the request text we already read to the target FIRST,
                //    otherwise the server never sees the request line/headers.
                OutputStream toTarget = target.getOutputStream();
                // TODO: write requestHead's bytes to toTarget and flush.

                // 5. Relay both directions concurrently.
                Thread c2t = new Thread(() -> relay(safeIn(client), safeOut(target), "client->target"));
                Thread t2c = new Thread(() -> relay(safeIn(target), safeOut(client), "target->client"));
                c2t.start();
                t2c.start();
                c2t.join();
                t2c.join();
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Proxy relay ended: " + e.getMessage());
        }
    }

    /*
     * readRequestHead -- read the request line and all header lines (up to the
     *                    blank line) and return them as one string.
     * called by : serviceClient
     * returns   : the request head text, or null if the client closed early
     *
     * TODO: read line by line until you hit an empty line (end of headers).
     *       Rebuild each line WITH its "\r\n" so the text you forward is valid
     *       HTTP. Do NOT read the body here.
     */
    private static String readRequestHead(InputStream clientIn) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientIn, StandardCharsets.UTF_8));
        StringBuilder head = new StringBuilder();
        // TODO: loop reading lines; stop at the blank line that ends the headers.
        return head.toString();
    }

    /*
     * parseTargetHost -- pull the destination host out of the request head.
     * called by : serviceClient
     *
     * TODO: get it from the absolute URI in the request line
     *       ("GET http://HOST:PORT/path HTTP/1.0") or from the "Host:" header.
     *       Return just the host name (no port), or null if none found.
     */
    private static String parseTargetHost(String requestHead) {
        return null; // TODO
    }

    /*
     * parseTargetPort -- pull the destination port out of the request head.
     * called by : serviceClient
     *
     * TODO: if the host is given as "host:port" use that port; otherwise
     *       default to 80.
     */
    private static int parseTargetPort(String requestHead) {
        return 80; // TODO
    }

    /* ---- working helpers below: no changes needed ---- */

    /* relay -- copy bytes from in to out until the source is exhausted. */
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
            // connection closed by one side - normal end of relay
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
