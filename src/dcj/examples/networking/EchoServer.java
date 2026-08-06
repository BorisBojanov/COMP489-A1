package dcj.examples.networking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TOOL: java.net.ServerSocket  (TCP server endpoint)
 *
 * At the core of Java's TCP networking are Socket (client side) and
 * ServerSocket (server side). A ServerSocket is bound to a port and its
 * accept() method BLOCKS until a client connects, then returns a new Socket
 * dedicated to that one client.
 *
 * This echo server reads a line from the client and writes it straight back.
 * It handles one client at a time; a real server hands each accepted Socket to
 * a worker thread (see the "multithreaded server" note in the unit).
 *
 * Run this first:   java dcj.examples.networking.EchoServer 5000
 * Then in another terminal:  java dcj.examples.networking.EchoClient localhost 5000
 */
public class EchoServer {

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5000;

        // try-with-resources closes the ServerSocket automatically.
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("EchoServer listening on port " + port + " ... (Ctrl+C to stop)");

            while (true) {
                // accept() blocks until a client connects.
                try (Socket client = server.accept()) {
                    System.out.println("Connected: " + client.getInetAddress().getHostAddress()
                            + ":" + client.getPort());

                    // A Socket exposes byte streams; we wrap them for text I/O.
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(client.getInputStream()));
                    // autoFlush=true so each println is pushed immediately.
                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);

                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("  received: " + line);
                        if ("bye".equalsIgnoreCase(line)) {
                            out.println("Goodbye!");
                            break;
                        }
                        out.println("echo: " + line);   // send it back
                    }
                    System.out.println("Client disconnected.");
                } catch (IOException e) {
                    System.out.println("Connection error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Could not listen on port " + port + ": " + e.getMessage());
        }
    }
}
