package dcj.examples.networking;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * TOOL: java.net.InetAddress  (IP addressing)
 *
 * An InetAddress represents an IP address. Before you can communicate with
 * another party you must be able to address your messages. On IP networks the
 * addressing scheme is based on hosts (a name and a numeric address) and ports.
 *
 * Key methods shown here:
 *   InetAddress.getLocalHost()        - this machine
 *   InetAddress.getByName(host)       - resolve a hostname (DNS) to one address
 *   InetAddress.getAllByName(host)    - all addresses a name maps to
 *   getHostName() / getHostAddress()  - name and dotted-decimal address
 *
 * Run:  java dcj.examples.networking.InetAddressDemo [hostname]
 */
public class InetAddressDemo {

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "www.oreilly.com";

        try {
            InetAddress local = InetAddress.getLocalHost();
            System.out.println("This machine:");
            System.out.println("  host name : " + local.getHostName());
            System.out.println("  ip address: " + local.getHostAddress());
        } catch (UnknownHostException e) {
            System.out.println("Could not determine local host: " + e.getMessage());
        }

        System.out.println();
        System.out.println("Looking up: " + host);
        try {
            // A single (canonical) address for the name.
            InetAddress addr = InetAddress.getByName(host);
            System.out.println("  primary address: " + addr.getHostAddress());

            // A DNS name can map to many addresses (load balancing, IPv4 + IPv6).
            InetAddress[] all = InetAddress.getAllByName(host);
            System.out.println("  all addresses (" + all.length + "):");
            for (InetAddress a : all) {
                System.out.println("    " + a.getHostAddress()
                        + (a instanceof java.net.Inet6Address ? "  (IPv6)" : "  (IPv4)"));
            }
        } catch (UnknownHostException e) {
            // Thrown when the name cannot be resolved (bad name or no network/DNS).
            System.out.println("  Unknown host: " + e.getMessage());
        }
    }
}
