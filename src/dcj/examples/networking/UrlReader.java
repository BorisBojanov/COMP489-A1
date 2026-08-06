package dcj.examples.networking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;

/**
 * TOOL: java.net.URL  (high-level HTTP access)
 *
 * A URL identifies a resource by protocol, host, port and document. The URL
 * class lets you connect and download it with almost no code. openStream() is
 * the simplest of its three access methods (openStream / openConnection /
 * getContent) - it returns an InputStream of the resource's bytes.
 *
 * Requires internet access to actually fetch a page.
 *
 * Run:  java dcj.examples.networking.UrlReader https://www.oreilly.com/
 *
 * NOTE: new URL(String) is deprecated on newer JDKs. The modern, recommended
 * way is: URL url = URI.create(spec).toURL();  (used below).
 */
public class UrlReader {

    public static void main(String[] args) {
        String spec = (args.length > 0) ? args[0] : "https://www.oreilly.com/";

        try {
            URL url = URI.create(spec).toURL();     // modern replacement for new URL(spec)
            System.out.println("Fetching: " + url);

            int lines = 0;
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(url.openStream()))) {
                String line;
                while ((line = in.readLine()) != null && lines < 15) {
                    System.out.println(line);
                    lines++;
                }
            }
            System.out.println("... (printed first " + lines + " lines)");
        } catch (IOException e) {
            System.out.println("Could not read URL: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Malformed URL: " + spec);
        }
    }
}
