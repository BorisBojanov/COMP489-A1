package dcj.examples.networking;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Driver for the piped-stream example. It makes TWO pairs of connected pipes so
 * the client and server can talk in both directions, then starts both threads.
 *
 * The important idea: the pipes are connected to each other inside ONE process;
 * nothing is sent over the network. This is a clean way for cooperating threads
 * to exchange data.
 *
 * Run:  java dcj.examples.networking.PipedStreamExample
 */
public class PipedStreamExample {

    public static void main(String[] args) {
        PipedInputStream pinc;
        PipedInputStream pins;
        PipedOutputStream poutc;
        PipedOutputStream pouts;

        try {
            pinc = new PipedInputStream();
            pins = new PipedInputStream();
            // client writes -> server reads
            poutc = new PipedOutputStream(pins);
            // server writes -> client reads
            pouts = new PipedOutputStream(pinc);
        } catch (IOException e) {
            System.out.println("Failed to build piped streams: " + e.getMessage());
            return;
        }

        PipedClient client = new PipedClient(pinc, poutc);
        PipedServer server = new PipedServer(pins, pouts);

        System.out.println("Starting server...");
        server.start();
        System.out.println("Starting client...");
        client.start();

        // Wait for both threads to finish.
        try {
            server.join();
            client.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Done.");
    }
}
