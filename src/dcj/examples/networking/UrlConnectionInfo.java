package dcj.examples.networking;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

/**
 * TOOL: java.net.URLConnection  (metadata about a URL's resource)
 *
 * url.openConnection() returns a URLConnection you can query for the resource's
 * content length, content type, encoding, and last-modified time BEFORE (or
 * instead of) downloading the body. getContentType() is what the ContentHandler
 * mechanism uses to pick a handler for the data.
 *
 * Requires internet access.
 *
 * Run:  java dcj.examples.networking.UrlConnectionInfo https://www.oreilly.com/
 */
public class UrlConnectionInfo {

    public static void main(String[] args) {
        String spec = (args.length > 0) ? args[0] : "https://www.oreilly.com/";

        try {
            URL url = URI.create(spec).toURL();
            URLConnection conn = url.openConnection();
            conn.connect();

            System.out.println("URL           : " + url);
            System.out.println("Content-Type  : " + conn.getContentType());
            System.out.println("Content-Length: " + conn.getContentLength());
            System.out.println("Encoding      : " + conn.getContentEncoding());
            System.out.println("Last-Modified : " + conn.getLastModified());
            System.out.println("Date          : " + conn.getDate());
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Malformed URL: " + spec);
        }
    }
}
