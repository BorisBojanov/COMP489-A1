package comp489.a1;

import java.io.BufferedReader;
import java.io.IOException;
// import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Program: SimpleClient
 * Course  : COMP 489 - Assignment 1
 * 
 * Description:
 * Simple HTTP client that communicates with a proxy server.
 *
 * Architecture:   Client  <->  Proxy
 *
 * The client:
 *   1. asks the user for a resource URL,
 *   2. sends the request TO THE PROXY (not straight to the web server),
 *   3. reads whatever comes back, and
 *   4. prints it to the screen (no HTML rendering required).
 *
 * It is a plain TCP Socket client. Start by pointing it at a real web server
 * (google.com:80) to confirm you can send a request and read a response, THEN
 * switch it to talk to your proxy.
 * 
 * need to read the response body from the raw InputStream by byte count 
 * (using the Content-Length you parse from the headers) 
 * rather than readLine
 * 
 * Example HTTP request:
 *   GET /index.html HTTP/1.0
 *   Host: localhost
 *
 * Example HTTP response:
 *   HTTP/1.0 200 OK
 *   Content-Length: 1234
 *
 *   <body of the response>
 * 
 * Example HTTP/1.1 request:
 *   GET /index.html HTTP/1.1
 *   Host: www.example.com
 *   User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
 *   Accept: text/html,application/xhtml+xml
 *   Accept-Language: en-US,en;q=0.9
 *   Connection: keep-alive
 * 
 * Example of an HTTP POST request:
 *   POST /upload/file HTTP/1.1
 *   Host: api.example.com
 *   Content-Type: application/pdf
 *   Content-Length: 1048576
 *
 *   [RAW BINARY BYTES OF THE PDF FILE]
 * 
 * 
 * The user is supposed to type a URL, not a path. The assignment says the client lets the user “input the resource URL.” 
 *  Right now you ask for /index.html and send Host: localhost, which is the proxy’s own name. 
 *      Look at what the proxy receives.
 *          Last time I asked what the proxy has to extract from GET http://example.com/page.html HTTP/1.0. 
 *          Which of those pieces does your client currently have no way to send?
 * 
 * What happens when there is no Content-Length? 
 *      When no length is given, the body ends when the server closes the connection. So you read until end of stream (read() returns -1).
 *  
 * Body only gets read when contentLength > 0. Predict what the client prints for a 404 page, 
 *  for https://www.google.com (assignment test), and for any server that doesn’t send that header. 
 *  Then think about how an HTTP/1.0 client knows the body has ended when no length is given. 
 *      JNP Ch 6 (pp169–175) talks about this.
 * 
 * 
 * A valid HTTP request with a body of size 0 requires a Content-Length: 
 *  0 header to explicitly state that no data is being sent in the body. 
 *  This is most commonly used with POST, PUT, or PATCH methods when triggering an action that doesn't require extra payload parameters.
 *  Example:
 *  POST /api/v1/users/42/activate HTTP/1.1
 *      Host: example.com
 *      Authorization: Bearer mock_token_12345
 *      Content-Length: 0
 * 
 * What if the server never answers?  
 * What does the user see if the proxy accepts the connection and then goes silent? (SO_TIMEOUT)
 */

public class SimpleClient {

    // Used when the user just presses Enter. SimpleWebServer listens on 9806 by default.
    private static final String DEFAULT_URL = "http://localhost:9806/";

    public static void main(String[] args) {
        // For early testing, default to a real server; later use your proxy host/port.
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 8888;
        int timeoutMs = 5000; // 5 seconds
        int maxwaitMs = 7000; // 7 seconds
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter resource URL to request (e.g. http://example.com/page.html): ");
            String resource = console.readLine();

            // Blank input -> default to a FULL URL (a bare "/" has no host for the proxy to use)
            if (resource == null || resource.isBlank()) {
                resource = DEFAULT_URL;
                System.out.println("No URL entered, using default: " + resource);
            }
            resource = resource.trim();

            // Check the input really is an absolute http(s) URL: scheme://host[:port]/path
            URI target;
            try {
                target = new URI(resource);          // URISyntaxException if malformed (e.g. spaces)
            } catch (URISyntaxException e) {
                System.out.println("Not a valid URL: " + e.getMessage());
                return;
            }
            String scheme = target.getScheme();
            if (scheme == null || target.getHost() == null) {
                System.out.println("Please enter a full URL, e.g. " + DEFAULT_URL);
                return;
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                System.out.println("Unsupported scheme '" + scheme + "'. Use http:// or https://");
                return;
            }
            // Host header = the TARGET server, not the proxy we connect to
            String hostHeader = target.getHost() + (target.getPort() != -1 ? ":" + target.getPort() : "");

            MyHttp response = new MyHttp();

            // Create an UNCONNECTED socket, connect with a timeout, THEN get the streams.
            // (Calling getOutputStream() before connect() throws "Socket is not connected".)
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);  // connect timeout (ms)
                socket.setSoTimeout(maxwaitMs);                                // SO_TIMEOUT: max wait per read()
                PrintWriter out = new PrintWriter(socket.getOutputStream(), false);

                // 1. Send request: absolute URL in the request line, target in Host:
                sendInitialRequest(out, resource, hostHeader);
                // Half closed in both directions 
                socket.shutdownOutput();   //proxy: request complete. Just READ the response.

                // 2. Parse status line + headers byte-by-byte, then the body (MyHttp.setHttp)
                response.setHttp(socket);

                // 3. Print the response
                System.out.println(response.protocol + " " + response.statusCode + " " + response.statusMessage);
                response.rawHeaderlines.forEach(System.out::println);
                System.out.println();
                System.out.write(response.body, 0, response.body.length);   // raw bytes
                System.out.println();
                System.out.flush();

            } catch (SocketTimeoutException e) {
                // Must come BEFORE IOException (it is a subclass)
                System.out.println("Timed out: no response from " + host + ":" + port
                        + " (connect limit " + timeoutMs + " ms, read limit " + maxwaitMs + " ms).");
            } catch (IOException e) {
                System.out.println("Client error: " + e.getMessage()
                        + "  (is the proxy running on " + host + ":" + port + "?)");
            }

        } catch (IOException e) {
            System.out.println("Could not read from the console: " + e.getMessage());
        }
    }

    private static void sendInitialRequest(PrintWriter out, String resource, String host) {
        out.print("GET " + resource + " HTTP/1.0\r\n");
        out.print("Host: " + host + "\r\n");
        out.print("\r\n");     // blank line ends the request headers
        out.flush();           // IMPORTANT: flush or nothing is sent
    }


}
   