package dcj.examples.networking;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * TOOL: java.net.MulticastSocket  (one-to-many UDP broadcast)
 *
 * MulticastSocket extends DatagramSocket. Multicasting broadcasts data to many
 * agents at once (as opposed to unicast between two). Any agent that joins the
 * multicast group (a class-D address, 224.0.0.0 - 239.255.255.255) receives the
 * packets sent to it. Because it is built on UDP, delivery is not guaranteed.
 *
 * Run this first:  java dcj.examples.networking.MulticastReceiver 230.0.0.1 4446
 * Then:            java dcj.examples.networking.MulticastSender  230.0.0.1 4446 "hello group"
 *
 * NOTE: joinGroup(InetAddress) is the classic (now deprecated) form used in the
 * textbook; it still works. The modern form is
 * joinGroup(new InetSocketAddress(group, port), networkInterface).
 */
@SuppressWarnings("deprecation")
public class MulticastReceiver {

    public static void main(String[] args) {
        String groupAddr = (args.length > 0) ? args[0] : "230.0.0.1";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 4446;

        try (MulticastSocket socket = new MulticastSocket(port)) {
            InetAddress group = InetAddress.getByName(groupAddr);
            socket.joinGroup(group);                 // start listening on the group
            System.out.println("Joined multicast group " + groupAddr + " on port " + port
                    + " ... (Ctrl+C to stop)");

            byte[] buffer = new byte[1024];
            for (int i = 0; i < 20; i++) {           // receive up to 20 messages then leave
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                System.out.println("group message: " + msg);
                if ("bye".equalsIgnoreCase(msg.trim())) break;
            }
            socket.leaveGroup(group);                // stop listening
        } catch (IOException e) {
            System.out.println("Multicast error: " + e.getMessage());
        }
    }
}
