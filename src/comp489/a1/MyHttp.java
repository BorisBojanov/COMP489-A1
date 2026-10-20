package comp489.a1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.net.URISyntaxException;


public class MyHttp {
    public String protocol;  // HTTP protocol version, e.g., "HTTP/1.1"
    public int statusCode;  // HTTP response status code, e.g., 200, 404
    public String statusMessage;  // HTTP response status message, e.g., "OK", "Not Found"
    public String method;  // HTTP request method, e.g., "GET", "POST"
    
    private String rawUri;
    public String uri;      // Identifies a resource by name, location, or both. Just names the resource, doesn't tell you were to find it.
                            // "/index.html" or the full resource URL as input by the user
    public String url;      // URL of the resource, may be relative or absolute.
    public String urlfull;  // Full URL of the resource, including scheme, host, and path.
    public String host;
    public int port;
    public String pathAndQuery;
    public int contentLength;
    public String contentType;
    public byte[] body;
    public List<String> rawHeaderlines = new ArrayList<>(); // cannot make this static because each instance has its own headers list


    public MyHttp() {
        this.contentLength = 0;
        this.contentType = "";
        this.body = new byte[0];
    }


    private String readLineBytes(InputStream in)  throws java.io.IOException {
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

    public void setHttp(Socket socket) throws IOException {
        InputStream rawIn = socket.getInputStream(); // Keeps the raw stream available
        parseHeader(rawIn);
        parseBody(rawIn);
    }
        
    // Parses the HTTP header and puts into HttpResponse object relevant information into the response
    private void parseHeader(InputStream headerIn) throws IOException {
        // Headers are safe to read line-by-line using readLine()
        // Strip the network newline codes for clean string comparison/printing
        String line = readLineBytes(headerIn);
        if (line == null || line.isBlank()) return; // client closed / sent nothing
        parseStartLine(line);

        // Loop through each header line 
        //      until we reach the blank line separating headers from the body
        String hostHeader = "";
        while ((line = readLineBytes(headerIn)) != null && !line.isBlank()) {
            int colonIndex = line.indexOf(':');                             // First colon separates header name from value
            if (colonIndex < 0) continue;                                       // Skip lines without a colon
            
            String name = line.substring(0, colonIndex).trim().toLowerCase();
            String value = line.substring(colonIndex + 1).trim();

            String cleanLine = line.replace("\r", "").replace("\n", "");

            switch (name) {
                case "host":
                    hostHeader = value;
                    rawHeaderlines.add(cleanLine);
                    break;
                case "content-length":
                    try { 
                        this.contentLength  = Integer.parseInt(value);
                    } catch (NumberFormatException e) { this.contentLength = 0;}
                    rawHeaderlines.add(cleanLine);
                    break;
                case "content-type":
                    this.contentType = value;
                    rawHeaderlines.add(cleanLine);
                    break;
                default: 
                    // add all other headers to rawHeaderlines
                    rawHeaderlines.add(cleanLine);
                    break;
            }
        }
        // Parse the components if there is a  valid requestLine
        resolverDestination(hostHeader);
    }




    private void parseStartLine(String line) {
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            return;
        }

        if (parts[0].toUpperCase().startsWith("HTTP/")) {            // response
            this.protocol = parts[0];
            try { this.statusCode = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { }
            this.statusMessage = (parts.length > 2) ? parts[2] : "";
        } else {                                                            // request
            this.method   = parts[0];
            this.rawUri   = parts[1];                                       // exactly as sent
            this.protocol = (parts.length > 2) ? parts[2] : "HTTP/1.0";
        }
    }

    /*
     * resolverDestination -- work out the real target host, port and path.
     *   Port precedence (first one found wins):
     *     1. an explicit port in an absolute URL   GET http://localhost:8080/x HTTP/1.0
     *     2. an explicit port in the Host: header  Host: localhost:8080
     *     3. the default port for the scheme       http=80, https=443, ftp=21
     *   A port that is present but invalid (e.g. "abc" or 99999) becomes 0, so
     *   ProxyServer rejects the request instead of silently trying port 80.
     * inputs    : hostHeader -- value of the Host: header ("" if none)
     * outputs   : sets this.host, this.port, this.uri, this.pathAndQuery, this.url
     * called by : parseHeader
     * will call : splitHostPort, parsePort, defaultPort
     */
    private void resolverDestination(String hostHeader) {
        if (rawUri == null) return;
        String scheme = "http";
        String h = null;
        int p = -1;
        String pathWithQuery = rawUri;
        if (rawUri.contains("://")){
            try {
                URI uri = new URI(rawUri);
                if (uri.getScheme() != null) {
                    scheme = uri.getScheme().toLowerCase();
                }
                h = uri.getHost();
                p = uri.getPort();              // -1 if none given
                if (p > 65535) p = 0;           // URI accepts any digits; TCP does not
                
                if (uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
                    pathWithQuery = "/";
                } else {
                    pathWithQuery = uri.getRawPath();
                }

                if (uri.getRawQuery() != null) {
                    pathWithQuery += "?" + uri.getRawQuery();
                }
            } catch (URISyntaxException e) {
                System.out.println("Invalid URI: probably bad URL in request line: " + rawUri);
            }
        }
        
        if (h == null && hostHeader != null && !hostHeader.isEmpty()) {   // origin form + Host:
            // The Host header carries no scheme, so if it has no port the
            // default below still comes from the scheme (http unless told otherwise).
            String[] hostAndPort = splitHostPort(hostHeader);
            h = hostAndPort[0];
            p = parsePort(hostAndPort[1]);
        }
        if (p == -1) {
            p = defaultPort(scheme);
        }

        this.host = h;                                   // null = could not determine
        this.port = p;
        this.uri  = pathWithQuery;                        // what the rewritten request line needs
        this.pathAndQuery = pathWithQuery; // Store the final path and query for later use
        if (h != null) { this.url = scheme + "://" + h + ":" + p + pathWithQuery; this.urlfull = this.url; }
    
    }   
 
    /*
     * splitHostPort -- split an authority string "host[:port]" into its two parts.
     *   Handles bracketed IPv6 literals such as "[::1]:8080".
     *   Also usable later for a "CONNECT host:443" request line (https tunnelling).
     * inputs    : authority, e.g. "localhost:8080", "www.google.com", "[::1]:8080"
     * outputs   : String[2] {host, portText}; portText is null when no port is given
     * called by : resolverDestination
     * will call : nothing
     */
    private static String[] splitHostPort(String authority) {
        String a = authority.trim();
        if (a.startsWith("[")) {                                  // IPv6 literal
            int close = a.indexOf(']');
            if (close > 0) {
                String rest = a.substring(close + 1);
                return new String[] { a.substring(1, close), rest.startsWith(":") ? rest.substring(1) : null };
            }
        }
        int colon = a.lastIndexOf(':');
        if (colon > 0 && colon == a.indexOf(':')) {                // exactly one colon: host:port
            return new String[] { a.substring(0, colon), a.substring(colon + 1) };
        }
        return new String[] { a, null };
    }

    /*
     * parsePort -- convert port text into a TCP port number.
     * inputs    : text -- the digits after the colon, or null
     * outputs   : 1..65535 if valid; -1 if no port was given (caller uses the
     *             scheme default); 0 if a port was given but is invalid
     * called by : resolverDestination
     * will call : nothing
     */
    private static int parsePort(String text) {
        if (text == null || text.isEmpty()) return -1;
        try {
            int n = Integer.parseInt(text.trim());
            return (n >= 1 && n <= 65535) ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /*
     * defaultPort -- the well-known port for a URL scheme.
     *   Adding a new protocol later only means adding a case here.
     * inputs    : scheme in lower case ("http", "https", "ftp", ...)
     * outputs   : port number
     * called by : resolverDestination
     * will call : nothing
     */
    private int defaultPort(String scheme) {
        switch (scheme) {
            case "https": return 443;
            case "ftp" : return 21;
            case "http": return 80;
            default: return 80;
        }
    }

    // private String parseRequestLine(String requestLine) {
    //     // Example request line: "GET /path/to/resource?query=1 HTTP/1.1"
    //     String[] parts = requestLine.split(" ");
    //     if (parts.length >= 3) {
    //         String method = parts[0]; // e.g., "GET"
    //         String rawUri = parts[1];  // e.g., "/path/to/resource?query=1"
            
    //         URI uri = parseUri(rawUri);
    //         String path = uri.getPath();
    //         String fullurl = fullURL(uri);

    //         this.urlfull = fullurl; // Store the full URL for later use
    //         this.method = method; // Store the HTTP method for later use
    //         this.rawUri = rawUri; // Store the raw URI for later use in fullURL()
    //         return path; // Return the requested path
    //     }
    //     return null;
    // }

    // private URI parseUri(String rawUri) {
    //     // Represents the resource identifier as sent by the client
    //     try {
    //         URI uri = new URI(rawUri);
    //         System.out.println("Parsed URI Path: " + uri.getPath());
    //         System.out.println("Parsed URI Query: " + uri.getQuery());
    //         return uri;
    //     } catch (URISyntaxException e) {
    //         e.printStackTrace();
    //         return null;
    //     }
    // }

    // private String fullURL(URI uri){
    //    try {
    //     if (host != null) {
    //         // Determine the protocol (assume http for standard raw sockets unless handling SSL/TLS)
    //         String protocol = "http://"; 
            
    //         // Construct the string representation of the full URL
    //         String fullUrlString = protocol + host + rawUri;
            
    //         // Convert to java.net.URL objects
    //         // URL fullUrl = new URL(fullUrlString);
    //         URL fullUrl = uri.toURL();
    //         System.out.println("Full URL String: " + fullUrlString);
    //         System.out.println("Base URL (No query): " + fullUrl.getProtocol() + "://" + fullUrl.getHost() + ":" + fullUrl.getPort() + fullUrl.getPath());
    //         return fullUrlString;
    //     } else {
    //         System.out.println("Could not parse the full URL: 'host' header is missing");
    //     }
    //    } catch (MalformedURLException e) {
    //        e.printStackTrace();
    //    }
    //     return null;
    // }

    // Parses the HTTP body and stores it into the HttpResponse object
    private void parseBody(InputStream rawIn) throws IOException {
        // For binary resources, read bytes directly from the input stream
        int contLength = this.contentLength;
        byte[] bodyBuffer = new byte[contLength];
        int totalBytesRead = 0;

        while (totalBytesRead < contLength) {
            // Read directly from the stream into our byte array offset
            int bytesRead = rawIn.read(bodyBuffer, totalBytesRead, contLength - totalBytesRead);
            if (bytesRead == -1) {
                System.out.println("Warning: Server closed connection before sending full payload.");
                System.out.println("Total bytes read: " + totalBytesRead + " out of " + contLength);
                break; // Stream ended early
            }
            totalBytesRead += bytesRead;
        }
        
        // // Debug http text bodies to screen:
        // System.out.write(bodyBuffer, 0, totalBytesRead);
        // System.out.flush(); 
        // Files.write(filePath, bodyBuffer); // If it's an image, write bodyBuffer out to a local file instead of console!

        this.body = bodyBuffer;
    }
}
