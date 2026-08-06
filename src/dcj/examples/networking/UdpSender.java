package dcj.examples.networking;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * TOOL: java.net.DatagramSocket + DatagramPacket  (UDP) - sending side.
 *
 * Each DatagramPacket carries a data buffer plus the destination address and
 * port. We build the packet and call send(). If we don't care which local port
 * is used we create the DatagramSocket with no argument.
 *
 * Run:  java dcj.examples.networking.UdpSender <host> <port> <message...>
 * e.g.  java dcj.examples.networking.UdpSender localhost 5000 hi there
 */
public class UdpSender {

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;
        String message = (args.length > 2)
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "hi there";

        // No port argument -> the OS picks an unused local port for us.
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(host);
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            socket.send(packet);

            System.out.println("Sent \"" + message + "\" to " + host + ":" + port
                    + "  (from local port " + socket.getLocalPort() + ")");
        } catch (IOException e) {
            // Sending can raise IOException if transmission fails.
            System.out.println("UDP send failed: " + e.getMessage());
        }
    }
}
