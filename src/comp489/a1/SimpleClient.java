package comp489.a1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ============================================================================
 * ASSIGNMENT 1 - STARTER SCAFFOLD (client)   ***implement the TODOs yourself***
 * ----------------------------------------------------------------------------
 * Do NOT post completed assignment code to the Unit 4 Discussion. This file is
 * only a starting structure to help you begin; the graded logic is yours.
 * ============================================================================
 *
 * Architecture:   Client  <->  Proxy  <->  Web Server
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
 */
public class SimpleClient {

    public static void main(String[] args) {
        // For early testing, default to a real server; later use your proxy host/port.
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 8888;

        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter resource path to request (e.g. /index.html): ");
            String resource = console.readLine();
            if (resource == null || resource.isBlank()) {
                resource = "/";
            }

            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), false);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                // --- send a minimal HTTP/1.0 request line ---
                // TODO: build the request line your proxy/server expects.
                out.print("GET " + resource + " HTTP/1.0\r\n");
                out.print("Host: " + host + "\r\n");
                out.print("\r\n");     // blank line ends the request headers
                out.flush();           // IMPORTANT: flush or nothing is sent

                // --- read and print the whole response ---
                // TODO: for binary resources (images) read bytes, not lines.
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage()
                    + "  (is the server/proxy running on " + host + ":" + port + "?)");
        }
    }
}
   