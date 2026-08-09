package comp489.a1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * https://www.geeksforgeeks.org/java/setting-up-proxy-connection-to-a-system-in-java/
 * 
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
 * 
 * 
 * TODO: 1. Read the request line + headers from the client (line by line).
 * TODO: 2. Determine the target: from the Host: header, or from an absolute URI in the request line (GET http://host/path).
 * TODO: 3. Open new Socket(targetHost, targetPort).
 * TODO: 4. Write the request bytes you already read to the target first.
 * TODO: 5. Then start your existing two-thread relay to pump the rest and stream the response back.
 */
public class ProxyServer {

    public static void main(String[] args) {
        String host = "myProxyServer";
        int remotePort = 80;
        int localPort = 8888;
        
        int listenPort = (args.length > 0) ? Integer.parseInt(args[0]) : localPort;

        try (ServerSocket listen = new ServerSocket(listenPort)) {
            System.out.println("ProxyServer listening on port " + listenPort + " ... (Ctrl+C to stop)");
            
            // runServer(host, remotePort, localPort); // never returns

            while (true) {
                Socket client = listen.accept();
                // Give each client to its own thread so the proxy stays responsive.
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

        String targetHost = "example.com";   
        int targetPort = 80;
        
        // determine the TARGET host + port
        targetHost = getTargetHost(client);
        targetPort = getTargetPort(client);

        // Open a socket to the target web server and relay both directions concurrently.
        try (client;
            
            Socket target = new Socket(targetHost, targetPort)) {
            handleClient(client); // Read and print the request line + headers from the client

            // Pump client -> target on this thread's own worker...
            Thread client2target = new Thread(() -> relay(safeIn(client), safeOut(target), "client->target"));
            // ...and target -> client on another.
            Thread target2client = new Thread(() -> relay(safeIn(target), safeOut(client), "target->client"));

            client2target.start();
            target2client.start();
            client2target.join();
            target2client.join();
        } catch (IOException | InterruptedException e) {
            System.out.println("Proxy relay ended: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) throws IOException {
        // Wrap the socket input stream in a BufferedReader
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
        );

        // 1. Read the Request Line (First line of the HTTP Request)
        String requestLine = reader.readLine();
        if (requestLine == null) {
            return; // Client closed connection prematurely
        }
        System.out.println("--- REQUEST LINE ---");
        System.out.println(requestLine);

        // 2. Read the Headers line by line
        System.out.println("--- HEADERS ---");
        String headerLine;
        
        // Loop until an empty line is encountered
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            System.out.println(headerLine);
            
            // Optional: Parse the header key-value pair
            /*
            int colonIndex = headerLine.indexOf(":");
            if (colonIndex != -1) {
                String headerName = headerLine.substring(0, colonIndex).trim();
                String headerValue = headerLine.substring(colonIndex + 1).trim();
            }
            */
        }
        
        System.out.println("--- END OF HEADERS ---");
        
        // Note: Do not close the reader here if you intend to read the request body next!
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

    // String targetHost , int targetPort
    private static String getTargetHost(Socket client) {
        // TODO: implement logic to extract the target host from the client request
        String hostname = client.getInetAddress().getHostName();
        return hostname != null ? hostname : "localhost";
    }
    private static int getTargetPort(Socket client) {
        // TODO: implement logic to extract the target port from the client request
        int targetport = client.getPort();
        return targetport != 0 ? targetport : 80; // default to port 80 if not specified
    }

    private static void runServer (String host, int remotePort, int localPort) throws IOException {
        // Create a ServerSocket to listen for connections
        ServerSocket serverSocket = new ServerSocket(localPort);

        final byte[] request = new byte[1024];
        byte[] reply = new byte[4096];

        while (true) {
            Socket client = null;
            Socket server = null;

            try {
                // Wait for a connection on the local port
                client = serverSocket.accept();

                InputStream streamFromClient = client.getInputStream();
                OutputStream streamToClient = client.getOutputStream();

                // Make a connection to the real server.
                // If we cannot connect to the server, send
                // an error to the client, disconnect, and
                // continue waiting for connections.
                try {
                    server = new Socket(host, remotePort);
                } catch (IOException e) {
                    PrintWriter out = new PrintWriter(streamToClient);
                    out.println("Proxy server cannot connect to " + host + ":" + remotePort + "");
                    out.flush();
                    client.close();
                    continue;
                }

                // Get server streams.
                InputStream streamFromServer= server.getInputStream();
                OutputStream streamToServer = server.getOutputStream();

                // a thread to read the client's requests
                // and pass them to the server. A separate
                // thread for asynchronous.
                Thread t = new Thread() {
                    public void run() {
                        int bytesRead;
                        try {
                            while ((bytesRead = streamFromClient.read(request)) != -1) {
                                streamToServer.write(request, 0, bytesRead);
                                streamToServer.flush();
                            }
                        } catch (IOException e) {
                            // ignore
                        }

                        // the client closed the connection
                        // to us, so close our connection to
                        // the server.
                        try {
                            streamToServer.close();
                        } catch (IOException e) {}
                        try {
                            streamFromClient.close();
                        } catch (IOException e) {}
                    }
                };

                // Start the client-to-server request thread
                // running
                t.start();

                // Read the server's responses
                // and pass them back to the client.
                int bytesRead;
                try {
                    while ((bytesRead = streamFromServer.read(reply)) != -1) {
                        streamToClient.write(reply, 0, bytesRead);
                        streamToClient.flush();
                    }
                } catch (IOException e) {
                }

                // The server closed its connection to us,
                // so we close our connection to our client.
                streamToClient.close();

            } catch (IOException e) {
                System.err.println(e);
            } finally {
                try {
                    if (server != null) server.close();
                    if (client != null) client.close();
                    // serverSocket.close(); // Do not close the server socket here; it should stay open to accept new connections.
                } catch (IOException e) {
                    
                }
            }
        }
    }
}
