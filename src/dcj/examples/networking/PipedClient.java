package dcj.examples.networking;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * TOOL: java.io.PipedInputStream / PipedOutputStream  (thread-to-thread I/O)
 *
 * Piped streams connect two THREADS in the SAME process: a PipedInputStream
 * reads whatever a connected PipedOutputStream writes. This is the in-process
 * cousin of socket streams and is handy for producer/consumer designs.
 *
 * Modernized from the textbook example:
 *   - uses writeUTF()/readUTF() instead of the deprecated readLine()
 *   - the thread ends by simply returning from run() (the book's Thread.stop()
 *     throws UnsupportedOperationException on modern JDKs).
 */
public class PipedClient extends Thread {

    private final PipedInputStream pin;
    private final PipedOutputStream pout;

    public PipedClient(PipedInputStream in, PipedOutputStream out) {
        this.pin = in;
        this.pout = out;
    }

    @Override
    public void run() {
        // Wrap data streams around the piped streams for typed I/O.
        DataInputStream din = new DataInputStream(pin);
        DataOutputStream dout = new DataOutputStream(pout);

        // Say hello to the server...
        try {
            System.out.println("PipedClient: writing greeting to server...");
            dout.writeUTF("hello from PipedClient");
            dout.flush();
        } catch (IOException e) {
            System.out.println("PipedClient: couldn't send greeting.");
            return;
        }

        // ...and read the response.
        try {
            System.out.println("PipedClient: reading response from server...");
            String response = din.readUTF();
            System.out.println("PipedClient: server said: \"" + response + "\"");
        } catch (IOException e) {
            System.out.println("PipedClient: failed to read response.");
        }
    }
}
