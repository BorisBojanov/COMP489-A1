package dcj.examples.networking;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * TOOL: java.io.PipedInputStream / PipedOutputStream - the server side.
 *
 * Waits for the client's "hello" on its input pipe, then writes a reply back on
 * its output pipe. Both threads exchange data purely in-memory, with no sockets.
 */
public class PipedServer extends Thread {

    private final PipedInputStream pin;
    private final PipedOutputStream pout;

    public PipedServer(PipedInputStream in, PipedOutputStream out) {
        this.pin = in;
        this.pout = out;
    }

    @Override
    public void run() {
        DataInputStream din = new DataInputStream(pin);
        DataOutputStream dout = new DataOutputStream(pout);

        // Wait for the client to say hello...
        try {
            System.out.println("PipedServer: reading from client...");
            String clientHello = din.readUTF();
            System.out.println("PipedServer: client said: \"" + clientHello + "\"");
        } catch (IOException e) {
            System.out.println("PipedServer: couldn't get hello from client.");
            return;
        }

        // ...and say hello back.
        try {
            System.out.println("PipedServer: writing response to client...");
            dout.writeUTF("hello, I am the server.");
            dout.flush();
        } catch (IOException e) {
            System.out.println("PipedServer: failed to reply to client.");
        }
    }
}
