package comp489.a1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/*
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
 * Because it must send and receive at the same time, each direction of the
 * relay is moved on its own THREAD (the relay() helper and the pump Runnable below). 
 *      This scaffold sets up the accept loop and the two-way pump skeleton; 
 *      you implement how the target host/port is chosen and any request
 *      rewriting your assignment requires.
 * 
 *  Inputs  : args[0] = listen port (optional, default 8888)
 *  Outputs : relays bytes between client and target; logs to stdout
 * 
 * 
 * 
1. Done serviceClient never reads the request

client.getLocalAddress().getHostName() is the address of this machine, the proxy itself. client.getPort() is the client’s temporary outgoing port, a random number like 52314.
Question: where in the program does the real destination information exist, and which of your own methods was written to get it out?

2. DONE == on strings

targetHost == "myproxyAssignment.com" compares object references, not text. In Java, what’s the right way to compare string contents?
A bigger design question: the assignment tests with localhost, 127.0.0.1, google.com and an ftp host. If the proxy only allows one hostname, which of those tests pass? Is a host allow-list something the assignment asks for?

3. TODO: Infinite loop in readRequestHead

Trace it with a two-line request. How many times is readLine() called in total? Look at what’s inside the while body.
Also think about this: BufferedReader reads ahead in chunks. If it swallows bytes beyond the blank line (say, a POST body), what happens to them when you later relay from client.getInputStream()? A request head that is consumed is also no longer available to forward. Where does the proxy get the text it sends to the target?

4. TODO: parseTargetHost splits the wrong thing

Take this head and write out what split(" ") returns:
  GET /testfile.html HTTP/1.0\r\nHost: localhost:8080\r\n\r\n

Will any element ever start with "host:" and also contain the host value? And what does parts[1] give you for that request, and what does URI.getHost() return for it?

The comment says a request may have an absolute URL or only a Host: header. The method is written as if everything were one line. What would be a better unit to split on first?
The debug println inside the loop will spam the console. Remove it before submission (the marks include “user-friendly”).

5. TODO: parseTargetPort

It’s fine for absolute URLs. But if the host came from a Host: header, does your port logic ever see the header? Also, what default would be right for https:// or ftp://, rather than 80? You don’t need to solve https and ftp today, but the design should allow for them.

6. TODO: Request rewriting

A client talking to a proxy sends GET http://localhost:8080/testfile.html HTTP/1.0. Your SimpleWebServer (and the spec) expects GET /testfile.html HTTP/1.0. If the proxy forwards the line unchanged, what does your web server do with it? That is the “request rewriting” your scaffold comment mentions.

7. Done Resource and shutdown issues

The original scaffold had try (client; ...). You dropped it, so the client socket is never closed. Check every exit path, including the “denied” branch.
join() waits for both pump threads. If the client keeps its side open after sending the request, does c2t ever end? Look up Socket.shutdownOutput() (JNP p250, “half-closed sockets”).

    Fixed by adding the client socket to the try-with-resources statement.

8. TODO: Tidy-up (documentation is 20% of the grade)

The file header still contains scaffold text (“This scaffold sets up...”, lines 29-33), and a “Do NOT post” note may have been left behind elsewhere. Rewrite it in your own words.
The unused inner class myHttpResponse and the commented-out block at the end of main should go, or be used. Dead code reads as unfinished.
Per the Notion requirements, each function header needs: name, description, inputs, outputs, “called by”, and “will call”. Yours have some of these, not all.
 */
public class ProxyServer {
    private static final int DEFAULT_LISTEN_PORT = 8888;
    private static final List<String> PROXY_HOSTNAMES = Arrays.asList("borisbojanovhomelab.com", "localhost", "127.0.0.1", "www.google.com", "BorisFileTransferProtocol.com");
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
                // Hand each client to its own thread so the proxy stays responsive.
                new Thread(() -> {
                    try {
                        serviceClient(client);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("Proxy could not listen on port " + listenPort + ": " + e.getMessage());
        }
    }


    /*
     * serviceClient -- handle a single client connection end to end.
     * called by : main (on a worker thread)
     * calls     : relay
     * arguments : client socket
     */
    private static void serviceClient(Socket client) throws IOException, InterruptedException{
        //   1. read the client's request and determine the TARGET host + port
        //      (parse the requested URL, or an initial "CONNECT host:port" line).
        // Need to go from Socket client -> targetHost:targetPort via the proxy
        String targetHost = "";      // derived from the client request
        int targetPort = -1;                    // derived from the client request
        // InputStream rawIn = client.getInputStream(); // Keeps the raw stream available
        MyHttp request = new MyHttp();
        request.setHttp(client); // This initializes the HTTP object with the client's request

        // targetHost = client.getLocalAddress().getHostName();
        // targetPort = client.getPort();
        targetHost = request.host;
        targetPort = request.port;
        

        // parsed the destination
        if (targetHost == null || targetHost.isEmpty() || targetPort <= 0) {
            System.out.println("Invalid target host or port");
            return;
        }

        // Allow access if the client is trying to reach the approved host
        if (PROXY_HOSTNAMES.contains(targetHost)) {
            // proceed with the connection
            //   2. open a socket to that target web server
            try ( 
                client;
                Socket target = new Socket(targetHost, targetPort) 
            ) {
                    
                //   3. relay both directions concurrently (client->target, target->client).
                // The head must be sent first, before any body content.
                String requestToServer = createRequestHead(request);
                OutputStream toTarget = target.getOutputStream();
                toTarget.write(requestToServer.getBytes());
                // toTarget.write(requestToServer.getBytes(StandardCharsets.US_ASCII));
                
                // Then send the body if it exits
                if (request.body != null && request.body.length > 0) {
                    toTarget.write(request.body);
                }
                toTarget.flush();


                // The two-way relay: Where the client socket is connected to the server's real 'target' socket once we have parsed the destination.
                // Use client -> target on this thread's own worker...
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
        } else {
            System.out.println("Access denied to host: " + targetHost);
            // Close the client socket, so denied hosts don't hang the client
            client.close();
            return;
        }

    }

    /*
     * Helper method to create the request head for forwarding to the target server. 
     * It assembles the all bytes sent to the target. In order: the rewritten request line, then every stored header line each ending in \r\n, then the blank line.
    */
    private static String createRequestHead (MyHttp request){
        StringBuilder requestHead = new StringBuilder();
        // Start with the request line
        //                                       method    uri           protocol
        //  SimpleWebServer expects to receive      GET /testfile.html HTTP/1.0
        //  the proxy received                      GET http://localhost:8080/testfile.html HTTP/1.0.
        requestHead.append(request.method).append(" ").append(request.uri).append(" ").append(request.protocol).append("\r\n");

        // Then append all the raw header lines
        for (String header : request.rawHeaderlines) {
            requestHead.append(header).append("\r\n");
        }
        
        // Finish off with a blank line. Must be "\r\n\r\n"
        requestHead.append("\r\n\r\n");
        return requestHead.toString();
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
