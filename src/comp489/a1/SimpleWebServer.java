package comp489.a1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
// import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
/**
 * ============================================================================
 * ASSIGNMENT 1 - STARTER SCAFFOLD (basic web server)  ***implement the TODOs***
 * ----------------------------------------------------------------------------
 * Do NOT post completed assignment code to the Unit 4 Discussion.
 * ============================================================================
 *
 * A basic HTTP server built on ServerSocket. For each connection it:
 *   1. reads the request line (e.g. "GET /index.html HTTP/1.0"),
 *   2. locates the requested file on disk, and
 *   3. either writes the file's BYTES back to the socket (works for HTML *and*
 *      binary files like JPEG - use byte streams), or returns a status message
 *      (404 Not Found, 400 Bad Request, ...).
 *
 * This scaffold accepts connections, parses the request line, and returns a
 * hard-coded 200 response so you can see the round-trip working. Replace the
 * TODO section with real file lookup + status handling.
 * 
 * decode → resolve → normalize → verify → stat.
 *  Establish the root as an absolute, normalized Path once
 * 
 */
public class SimpleWebServer {

    // private static final String DOCROOT = "C:\\Users\\boris\\ownCloud (2)\\Active_COURSES\\COMP489 Distributed Computing\\COMP489-A1\\COMP489-A1\\src\\comp489\\a1\\www";
    private static final String DROOT = Paths.get("src","comp489","a1","www").toAbsolutePath().normalize().toString();
    private static final Path root = Paths.get(DROOT).toAbsolutePath().normalize();
    // private static final String parentDir = Path.of("").toAbsolutePath().getParent().toString();
    private static final String INDEX_FILE = "index.html";
    public static void main(String[] args) {
        // print root, and if (!Files.isDirectory(root)) print an error and exit
        System.out.println("Document root: " + root);
        if (!root.toFile().isDirectory()) {
            System.out.println("Error: Document root is not a directory.");
            return;
        }
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9806;
        //Then make the docroot args[1] with a default, so you can override it without recompiling.
        String docRoot = (args.length > 1) ? args[1] : DROOT;

        try (ServerSocket server = new ServerSocket(port)) {
            
            System.out.println("SimpleWebServer listening on port " + port + " ... (Ctrl+C to stop)");
            
            while (true) {
                Socket client = server.accept();
                final Path dr = Paths.get(docRoot).toAbsolutePath().normalize();

                // Create a new thread to handle the client connecion
                new Thread(() -> {
                    try (client) {
                            handle(client, dr);
                    } catch (IOException e) {
                        System.out.println("Connection error: " + e.getMessage());
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("Could not listen on port " + port + ": " + e.getMessage());
        }
    }

    private static void sendBadRequest(OutputStream rawOut, String message) throws IOException {
        String body = message;
        String response = "HTTP/1.0 400 Bad Request\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + body.getBytes().length + "\r\n"
                + "\r\n"
                + body;
        rawOut.write(response.getBytes());
        rawOut.flush();
    }

    private static void send404(OutputStream rawOut, String message) throws IOException {
        String body = message;
        String response = "HTTP/1.0 404 Not Found\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + body.getBytes().length + "\r\n"
                + "\r\n"
                + body;
        rawOut.write(response.getBytes());
        rawOut.flush();
    }

    private static void sendConfirmation(OutputStream rawOut, String message) throws IOException {
        String body = message;
        String response = "HTTP/1.0 200 OK\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + body.getBytes().length + "\r\n"
                + "\r\n"
                + body;
        rawOut.write(response.getBytes());
        rawOut.flush();
    }

    private static void serve(OutputStream rawOut, Path imagePath) throws IOException {
        String contentType = Files.probeContentType(imagePath);
        long contentLength = Files.size(imagePath);
        String responseHeader = "HTTP/1.0 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + contentLength + "\r\n"
                + "\r\n";
        rawOut.write(responseHeader.getBytes());
        Files.copy(imagePath, rawOut);
        rawOut.flush();
    }
    
    private static void handle(Socket client, Path root) throws IOException {
        
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        OutputStream rawOut = client.getOutputStream();   // byte stream for the body

        // Read the request line: METHOD PATH VERSION
        String requestLine = in.readLine();
        System.out.println("request: " + requestLine);
        if (requestLine == null || requestLine.isBlank()) {
            return;
        }

        String[] parts = requestLine.split("\\s+");
        String method = (parts.length > 0) ? parts[0] : "";
        String path = (parts.length > 1) ? parts[1] : "/";

        // TODO (assignment):
        //   * reject anything that is not GET with 400 Bad Request
        //   * translate 'path' to a file under a document root
        //   * if the file exists: send "HTTP/1.0 200 OK", a Content-Type header,
        //     a blank line, then the file BYTES (read with a FileInputStream and
        //     write to rawOut so images survive)
        //   * if not: send "HTTP/1.0 404 Not Found" + a short message
        // 1. Reject anything that is not GET with 400 Bad Request
        if (!method.equals("GET")) {
            sendBadRequest(rawOut, "Method '" + method + "' not supported. Only GET is allowed.");
            return;
        }
        // 2. Strip the query string; Find indexOf('?')
        // In here we can assume the method is GET and we can handle the path to serve files.
        int queryIndex = path.indexOf("?");
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }
        // 3.URL-decode
        // %20 → space. URLDecoder.decode(path, StandardCharsets.UTF_8).
        /*
        Two things to know: it also converts + to a space, 
            which is correct for query strings but technically wrong for path segments — fine for this assignment, but be aware. 
        More importantly, you must decode before the security check, 
            because %2e%2e%2f decodes to ../. An attacker who can skip your check by encoding it has beaten you.
        */
        try {
            path = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
            //if it ends with '/' (or is just "/"), append the default document name
            if (path.endsWith("/")) {
                path += INDEX_FILE;
            }
            // strip leading '/' so resolve() doesn't discard root
            String rel = path.startsWith("/") ? path.substring(1) : path;
            
            // 2. Resolve Path (Throws InvalidPathException on illegal characters like : * < >)
            // Replace "server_root" with your actual project document root folder path
            // Use the actual document root
            // Path targetFile = docRoot.resolve(path.substring(1)).normalize();
            Path requestedFile = root.resolve(rel).normalize();   // resolve ONCE

            // Check th econtent type of the requested file using Files.probeContentType(requestedFile) and send the appropriate Content-Type header in the response. If the content type is null, you can default to "application/octet-stream" or "text/plain".
            if (!Files.isRegularFile(requestedFile) || !Files.isReadable(requestedFile)) {
                /*isRegularFile collapses "doesn't exist" and "is a directory" into one check*/
                send404(rawOut, "File not found: " + requestedFile.toString()); 
                return;
            }

            if (!requestedFile.startsWith(root)) {   // root must be absolute+normalized (it is)
                send404(rawOut, "Forbidden");        // or 403
                return;
            }

            // If file exists send "HTTP/1.0 200 OK", a Content-Type header, a blank line, then the file BYTES (read with a FileInputStream and write to rawOut so images survive)
            // sendConfirmation(rawOut, "File found: " + requestedFile.toString());
            serve(rawOut, requestedFile);

            //return result
        } catch (IllegalArgumentException e) {
            // Catching the RuntimeExceptions prevents them from bubbling up and killing the server
            sendBadRequest(rawOut, "Bad Request: Invalid or malformed URL path.");
            return; 
        }
        



    }
}
