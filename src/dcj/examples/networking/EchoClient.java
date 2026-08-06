package dcj.examples.networking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * TOOL: java.net.Socket  (TCP client endpoint)
 *
 * A Socket is created by specifying a host (name or InetAddress) and a port on
 * which a process is listening. Once connected you get an InputStream and an
 * OutputStream and read/write to communicate.
 *
 * This client reads lines you type at the console, sends them to EchoServer,
 * and prints the server's reply. Type "bye" to end.
 *
 * Run EchoServer first, then:
 *   java dcj.examples.networking.EchoClient localhost 5000
 */
public class EchoClient {

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             BufferedReader console = new BufferedReader(
                     new InputStreamReader(System.in))) {

            System.out.println("Connected to " + host + ":" + port
                    + ".  Type a message ('bye' to quit):");

            String userLine;
            while ((userLine = console.readLine()) != null) {
                out.println(userLine);                // send to server
                String reply = in.readLine();         // read server's reply
                if (reply == null) {
                    System.out.println("Server closed the connection.");
                    break;
                }
                System.out.println("server> " + reply);
                if ("bye".equalsIgnoreCase(userLine)) break;
            }
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + host);
        } catch (IOException e) {
            System.out.println("I/O error: " + e.getMessage()
                    + "  (Is EchoServer running on " + host + ":" + port + "?)");
        }
    }
}
