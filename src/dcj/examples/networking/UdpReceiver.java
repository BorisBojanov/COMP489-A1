package dcj.examples.networking;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * TOOL: java.net.DatagramSocket + DatagramPacket  (UDP)
 *
 * UDP sockets are connectionless: the sender transmits a packet and it either
 * arrives or it doesn't (no delivery/order guarantee). Overhead is low, so UDP
 * suits real-time audio/video, games, DNS, telemetry, etc.
 *
 * A DatagramSocket bound to a port receives DatagramPackets. The received
 * packet has the sender's address/port filled in as a side effect of receive().
 *
 * Run this first:  java dcj.examples.networking.UdpReceiver 5000
 * Then:            java dcj.examples.networking.UdpSender localhost 5000 "hi there"
 */
public class UdpReceiver {

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5000;

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UdpReceiver listening on UDP port " + port + " ... (Ctrl+C to stop)");
            byte[] buffer = new byte[1024];

            while (true) {
                // The packet's buffer will hold the received bytes.
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);   // blocks until a datagram arrives

                String msg = new String(packet.getData(), 0, packet.getLength());
                System.out.println("from " + packet.getAddress().getHostAddress()
                        + ":" + packet.getPort() + "  ->  " + msg);
            }
        } catch (IOException e) {
            System.out.println("UDP error: " + e.getMessage());
        }
    }
}
