package dcj.examples.networking;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * TOOL: java.io.DataInputStream / DataOutputStream  (portable binary I/O)
 *
 * These are FILTER streams that read/write Java primitive types (int, double,
 * boolean, UTF strings, ...) in a machine-independent binary format. A value
 * written on one platform reads back correctly on another - exactly what you
 * want for a network protocol.
 *
 * Here we write primitives into an in-memory buffer, then read them back in the
 * SAME ORDER. Over a network you would wrap a socket's streams instead:
 *     new DataOutputStream(socket.getOutputStream())
 *     new DataInputStream(socket.getInputStream())
 *
 * Run:  java dcj.examples.networking.DataStreamsDemo
 */
public class DataStreamsDemo {

    public static void main(String[] args) throws IOException {
        // --- write side ---
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream dout = new DataOutputStream(bytes)) {
            dout.writeUTF("temperature");   // length-prefixed string
            dout.writeInt(42);              // 4 bytes, big-endian
            dout.writeDouble(3.14159);      // 8 bytes
            dout.writeBoolean(true);        // 1 byte
        }
        byte[] wireBytes = bytes.toByteArray();
        System.out.println("Encoded " + wireBytes.length + " bytes of binary data.");

        // --- read side: same order as written ---
        try (DataInputStream din = new DataInputStream(
                new ByteArrayInputStream(wireBytes))) {
            String label = din.readUTF();
            int count = din.readInt();
            double value = din.readDouble();
            boolean flag = din.readBoolean();

            System.out.println("Decoded -> label=" + label
                    + ", count=" + count
                    + ", value=" + value
                    + ", flag=" + flag);
        }
    }
}
