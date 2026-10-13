package comp489.a1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
// import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.ByteArrayOutputStream;


/**
 * Simple HTTP client that communicates with a proxy server.
 * ASSIGNMENT 1 
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
 * TODO 2. The user is supposed to type a URL, not a path. The assignment says the client lets the user “input the resource URL.” 
 *  Right now you ask for /index.html and send Host: localhost, which is the proxy’s own name. 
 *      Look at what the proxy receives.
 *          Last time I asked what the proxy has to extract from GET http://example.com/page.html HTTP/1.0. 
 *          Which of those pieces does your client currently have no way to send?
 * 
 * TODO Issue: What happens when there is no Content-Length? 
 *  body only gets read when contentLength > 0. Predict what the client prints for a 404 page, 
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
 * TODO What if the server never answers? You open a Socket with no timeout. 
 *  What does the user see if the proxy accepts the connection and then goes silent? (JavaNetProgr Ch 8 covers SO_TIMEOUT, p261.)


 * Tidy-up before submit:
    response.contentLength is never set.
    myHttpResponse is an inner class, which is why you write new SimpleClient().new myHttpResponse(). 
        Ask yourself whether it needs to belong to an instance. A static nested class (or its own file) would be cleaner.
 */
public class SimpleClient {
    public class myHttpResponse {
        public myHttpResponse() {
            this.contentLength = 0;
            this.contentType = "";
            this.body = new byte[0];
        }
        public String uri;      // Identifies a resource by name, location, or both. Just names the resource, doesn't tell you were to find it.
                                // "/index.html" or the full resource URL as input by the user
        public String url;      // URL of the resource, may be relative or absolute.
        public String urlfull;  // Full URL of the resource, including scheme, host, and path.
        public int contentLength;
        public String contentType;
        public byte[] body;
    }
    public static void main(String[] args) {
        // For early testing, default to a real server; later use your proxy host/port.
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 8888;

        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter resource URL to request (e.g. http://example.com/page.html): ");
            String resource = console.readLine();
            if (resource == null || resource.isBlank()) {
                resource = "/";
            }
            myHttpResponse response = new SimpleClient().new myHttpResponse();


            // Notice: Do NOT wrap the InputStream in a BufferedReader entirely if you want raw access.
            // Instead, read from the raw InputStream, or wrap a temporary reader just for headers.
            try (Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), false);
                InputStream rawIn = socket.getInputStream(); // Keeps the raw stream available
            ){

                // 1. Send requst
                sendInitialRequest(out, resource, host);
                // 2. Parse Headers Line-by-Line using a custom line reader or safe wrapper
                // We parse manually from the stream or use a scanner to avoid over-consuming binary data.
                int contentLength = 0;
                contentLength = parseHeader(rawIn, response);
                response.contentLength = contentLength;
                if (contentLength <= 0) contentLength = 0;
                
                System.out.println("Headers read successfully.");
                
                // 3. Read the Body using the Content-Length count
                if (contentLength > 0) {
                    if (response.contentType != null) { System.out.println("Content-Type: " + response.contentType); }
                    System.out.println("--- BODY (Reading " + contentLength + " bytes) ---");
                    parseBody(contentLength, rawIn, response);
                }

            } catch ( IOException | NumberFormatException e){ 
                System.out.println("Socket socket\nPrintWriter out\nInputStream rawIn\nFailed to connect Error: " + e.getMessage() +  "\n For host:" + host + " \n At port: " + port);
            }

        } catch (IOException e) {
            System.out.println("BufferedReader console: \n\tClient error: " + e.getMessage()
                    + "  (is the server/proxy running on " + host + ":" + port + "?)");
        }
    }

    private static String readLineBytes(InputStream in)  throws java.io.IOException {
        /*
        Issue- When you call rawIn.read(), those bytes will be missing.
            
        BufferedReader automatically reads a large chunk of data (often 8KB) from the underlying socket stream into an internal memory buffer. 
            If the headers are small and the image data comes right after them, headerReader.readLine() 
            will accidentally read part of the binary image bytes (during chunking) into its buffer. 
            
        
        Fix- this helper method reads byte-by-byte from rawIn until it hits \r\n to parse headers. 
            That leaves the stream pointer perfectly positioned at the first byte of the binary body.
        */
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int i;

        while ((i = in.read())!= -1){
            buffer.write(i);
            // Look for the "Line Feed" character (\n) which ends an HTTP header line
            if (i == '\n') break;
        }
        // If stream ended and we read nothing, return null (End of Stream)
        if (buffer.size() == 0 ) return null;
        
        // Convert bytes to string and trim the trailing \r\n characters

        // return buffer.toString(java.nio.charset.StandardCharsets.US_ASCII);
        String out = buffer.toString(java.nio.charset.StandardCharsets.US_ASCII).replaceAll("\r?\n$", "");
        return out;

    }

    // Parses the HTTP header and puts into HttpResponse object relevant information into the response
    private static int parseHeader(InputStream headerIn, myHttpResponse response) throws IOException {
        // Headers are safe to read line-by-line using readLine()
        // Strip the network newline codes for clean string comparison/printing
        String line;
        
        int contentLength = 0;
        
        // Loop through each header line 
        //      until we reach the blank line separating headers from the body
        while ((line = readLineBytes(headerIn)) != null && !line.isBlank()) {

            String cleanLine = line.replace("\r", "").replace("\n", "");
            System.out.println(cleanLine);

            // Check for Content-Length (case-insensitive)
            if (cleanLine.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(cleanLine.split(":")[1].trim());
            }
            if (cleanLine.toLowerCase().startsWith("content-type:")) {
                // Store the content type
                String contType = cleanLine.split(":")[1].trim();
                response.contentType = contType;
            }
        }
        return contentLength;
    }

    // Parses the HTTP body and stores it into the HttpResponse object
    private static void parseBody(int contentLength, InputStream rawIn, myHttpResponse response) throws IOException {
        // For binary resources, read bytes directly from the input stream
        byte[] bodyBuffer = new byte[contentLength];
        int totalBytesRead = 0;

        while (totalBytesRead < contentLength) {
            // Read directly from the stream into our byte array offset
            int bytesRead = rawIn.read(bodyBuffer, totalBytesRead, contentLength - totalBytesRead);
            if (bytesRead == -1) {
                System.out.println("Warning: Server closed connection before sending full payload.");
                System.out.println("Total bytes read: " + totalBytesRead + " out of " + contentLength);
                break; // Stream ended early
            }
            totalBytesRead += bytesRead;
        }
        
        // // Debug http text bodies to screen:
        // System.out.write(bodyBuffer, 0, totalBytesRead);
        // System.out.flush(); 
        // Files.write(filePath, bodyBuffer); // If it's an image, write bodyBuffer out to a local file instead of console!

        response.body = bodyBuffer;
    }

    private static void sendInitialRequest(PrintWriter out, String resource, String host) {
        out.print("GET " + resource + " HTTP/1.0\r\n");
        out.print("Host: " + host + "\r\n");
        out.print("\r\n");     // blank line ends the request headers
        out.flush();           // IMPORTANT: flush or nothing is sent
    }


}
   