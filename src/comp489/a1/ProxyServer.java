package comp489.a1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
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
 * Tt must send and receive at the same time, each direction of the
 *      relay is moved on its own THREAD (the relay() helper and the pump Runnable below). 
 *      
 * This file implements the accept loop and the two-way relay threads; 
 *      the target host/port is chosen and any request as the assignment specifies.
 * 
 *  Inputs  : args[0] = listen port (optional, default 8888)
 *  Outputs : relays bytes between client and target; logs to stdout
 * 
1. Done serviceClient never reads the request

client.getLocalAddress().getHostName() is the address of this machine, the proxy itself. 

client.getPort() is the client’s temporary outgoing port (a random number)

Design question: 
    Where in the program does the real destination information exist, and which of your own methods was written to get it out?
    
    Tests with (localhost, 127.0.0.1, google.com and an ftp host) 
        If the proxy only allows one hostname, which of those tests pass? Is a host allow-list something the assignment asks for?


    BufferedReader reads ahead in chunks. If it swallows bytes beyond the blank line (say, a POST body), what happens to them when you later relay from client.getInputStream()? A request head that is consumed is also no longer available to forward. Where does the proxy get the text it sends to the target?

    readRequestHead was replaced by MyHttp.parseHeader, which calls readLineBytes once per
        loop (in the while condition) and reads byte-by-byte, so nothing past the blank line is swallowed.
        The head is kept in rawHeaderlines and rebuilt by createRequestHead; the body is read by Content-Length.

    join() waits for both pump threads. If the client keeps its side open after sending the request, does c2t ever end? Look up Socket.shutdownOutput() (JNP p250, “half-closed sockets”).

parseTargetHost: splits the request head into lines, then again to look for host. 
    Take this head and write out what split(" ") returns:
    GET /testfile.html HTTP/1.0\r\nHost: localhost:8080\r\n\r\n

Will any element ever start with "host:" and also contain the host value? 
what does parts[1] give you for that request, and what does URI.getHost() return for it?

Request may have an absolute URL or only a Host: header. 
    The head is split into lines first (readLineBytes), then each line at its first ':'.
    The host comes from the absolute URL if present, otherwise from the Host: header.

parseTargetPort 
    If the host came from a Host: header, the port logic ever see the header? 
    w\What default would be right for https:// or ftp://, rather than 80? 

    Moved this to MyHttp.resolverDestination: port = URL port, else Host: header port, else defaultPort(scheme)
    (http 80, https 443, ftp 21). Invalid ports become 0 and are rejected by serviceClient.


A client talking to a proxy sends:
     GET http://localhost:8080/testfile.html HTTP/1.0. 
SimpleWebServer expects:
     GET /testfile.html HTTP/1.0. 
    
    If the proxy forwards the line unchanged, what does your web server do with it? That is the “request rewriting” your scaffold comment mentions.



    Fixed by adding the client socket to the try-with-resources statement.


 */
public class ProxyServer {
    private static final int DEFAULT_LISTEN_PORT = 8888;
    private static final List<String> PROXY_HOSTNAMES = Arrays.asList("borisbojanovhomelab.com", "localhost", "127.0.0.1", "www.google.com", "BorisFileTransferProtocol.com");

    /*
     * main -- entry point. Opens the listening socket and accepts clients
     *         forever, handing each one to its own thread.
     * inputs    : args[0] = listen port (optional, default 8888)
     * outputs   : none returned; prints status messages to stdout
     * called by : the JVM
     * will call : serviceClient (on a new thread per client)
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
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("Proxy could not listen on port " + listenPort + ": " + e.getMessage());
        }
    }


    /*
     * serviceClient -- handle a single client connection end to end: parse the
     *   request, find the target host and port, check them against the allow-list,
     *   forward the rewritten request, then relay bytes both ways (with half-close)
     *   until the exchange ends.
     * inputs    : client -- the accepted client socket
     * outputs   : none returned; the client receives the target's response, or the
     *             connection is closed if the target is invalid or not allowed
     * called by : main (on a worker thread)
     * will call : MyHttp (constructor), MyHttp.setHttp, createRequestHead,
     *             relay (on two threads), safeIn, safeOut
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
            client.close(); // Close the client socket if the target host or port is invalid
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
                // A half-closed socket 
                // Use client -> target on this thread's own worker...
                Thread c2t = new Thread(() -> {
                    relay(safeIn(client), safeOut(target), "client->target");
                    try { target.shutdownOutput(); } catch (IOException ignored) { }
                });
                // target -> client on another.
                // When the target finishes, half-close the client side so the client sees
                // end-of-stream (needed when the response has no Content-Length).
                Thread t2c = new Thread(() -> {
                    relay(safeIn(target), safeOut(client), "target->client");
                    try { client.shutdownOutput(); } catch (IOException ignored) { }
                });

                c2t.start();
                t2c.start();

                t2c.join();     // wait for target->client relay to finish
                client.close(); // force-close the client socket if the client never closes its side
                c2t.join();     // now wait for client->target relay to finish
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
     * createRequestHead -- build the request head to forward to the target server:
     *   the rewritten request line (path only, e.g. "GET /x.html HTTP/1.0"), every
     *   stored header line, then the blank line.
     * inputs    : request -- the parsed client request (method, uri, protocol, rawHeaderlines)
     * outputs   : String -- the complete request head, each line ending in \r\n
     * called by : serviceClient
     * will call : nothing
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
        
        // Finish off with ONE blank line. Every header above already ended in
        // \r\n, so a single extra \r\n produces the required "\r\n\r\n".
        // (Appending "\r\n\r\n" here would add a stray empty line that the
        // server would read as the first two bytes of a POST body.)
        requestHead.append("\r\n");
        return requestHead.toString();
    }

    /* ---- working helpers below: no changes needed ---- */

    /*
     * relay -- copy bytes from in to out until the source reaches end-of-stream or
     *          the connection closes. One relay runs for each direction.
     * inputs    : in    -- source stream
     *             out   -- destination stream
     *             label -- name printed when finished, e.g. "client->target"
     * outputs   : none returned; bytes written to out; prints "<label> finished."
     * called by : serviceClient (the c2t and t2c threads)
     * will call : nothing
     */
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

    /*
     * safeIn -- get a socket's input stream without throwing (for use inside lambdas).
     * inputs    : s -- a connected socket
     * outputs   : the socket's InputStream, or null if it cannot be opened
     * called by : serviceClient
     * will call : nothing
     */
    private static InputStream safeIn(Socket s) {
        try { return s.getInputStream(); } catch (IOException e) { return null; }
    }

    /*
     * safeOut -- get a socket's output stream without throwing (for use inside lambdas).
     * inputs    : s -- a connected socket
     * outputs   : the socket's OutputStream, or null if it cannot be opened
     * called by : serviceClient
     * will call : nothing
     */
    private static OutputStream safeOut(Socket s) {
        try { return s.getOutputStream(); } catch (IOException e) { return null; }
    }
}
