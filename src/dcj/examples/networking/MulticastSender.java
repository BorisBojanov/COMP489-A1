package dcj.examples.networking;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * TOOL: java.net.MulticastSocket - sending side.
 *
 * Data is sent to all listening agents with send(), exactly like a normal UDP
 * datagram, but addressed to the multicast group address.
 *
 * Run:  java dcj.examples.networking.MulticastSender 230.0.0.1 4446 "hello group"
 */
public class MulticastSender {

    public static void main(String[] args) {
        String groupAddr = (args.length > 0) ? args[0] : "230.0.0.1";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 4446;
        String message = (args.length > 2)
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "hello group";

        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(groupAddr);
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(data, data.length, group, port);
            socket.send(packet);

            System.out.println("Broadcast \"" + message + "\" to group "
                    + groupAddr + ":" + port);
        } catch (IOException e) {
            System.out.println("Multicast send failed: " + e.getMessage());
        }
    }
}
