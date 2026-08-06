# COMP 489 – Assignment 1 (Java Networking)

A single Java project that holds **all your Assignment 1 work** plus runnable
**example programs for every networking tool** covered in Unit 4 (*Networking in
Java*, from Farley's *Java Distributed Computing*, Ch. 2).

It uses a plain `src` → `bin` layout with **no build tool** (no Maven/Gradle), so
it opens and compiles in **both Eclipse and VS Code** with nothing to install
beyond a JDK.

---

## Requirements

- A **JDK 17 or newer** (developed and tested on JDK 21).
  Check with: `java -version` and `javac -version`.

---

## Project layout

```
COMP489-A1/
├── .project, .classpath        Eclipse project files
├── .vscode/settings.json       VS Code (Java extension) source/output paths
├── src/
│   ├── dcj/examples/networking/   ← runnable tool examples (Unit 4)
│   │   ├── InetAddressDemo.java        InetAddress (IP addressing)
│   │   ├── EchoServer.java / EchoClient.java   TCP: ServerSocket + Socket
│   │   ├── UdpReceiver.java / UdpSender.java    UDP: DatagramSocket + DatagramPacket
│   │   ├── MulticastReceiver.java / MulticastSender.java   MulticastSocket
│   │   ├── PipedClient.java / PipedServer.java / PipedStreamExample.java   piped streams
│   │   ├── DataStreamsDemo.java        DataInputStream / DataOutputStream
│   │   ├── UrlReader.java              URL.openStream()
│   │   └── UrlConnectionInfo.java      URLConnection (headers/metadata)
│   ├── dcj/util/                  ← ClassLoader examples
│   │   ├── StreamClassLoader.java      abstract "load a class from a stream"
│   │   └── FileClassLoader.java        loads a class from disk (runnable)
│   └── comp489/a1/                ← YOUR Assignment 1 work (starter scaffolds)
│       ├── SimpleClient.java
│       ├── SimpleWebServer.java
│       └── ProxyServer.java
└── bin/                         compiled .class files (generated; git-ignored)
```

---

## Open in Eclipse

1. **File → Import… → General → Existing Projects into Workspace**.
2. Choose **Select root directory** and browse to this `COMP489-A1` folder.
3. Make sure `COMP489-A1` is checked, then **Finish**.
4. Eclipse builds automatically. To run a program, open it and press **Run ▶**
   (or right-click → *Run As → Java Application*).

> If Eclipse shows a JRE/JDK warning, set one via
> *Project → Properties → Java Build Path → Libraries*, or
> *Window → Preferences → Java → Installed JREs* (pick a JDK, not a JRE).

## Open in VS Code

1. Install the **Extension Pack for Java** (Microsoft) if you haven't.
2. **File → Open Folder…** → select this `COMP489-A1` folder.
3. The Java extension detects the project from `.classpath` / `.vscode/settings.json`.
4. Open any file with a `main` method and click **Run** above it (or use the
   *Run and Debug* panel).

## Compile & run from the command line

```bash
# from inside the COMP489-A1 folder
mkdir -p bin
javac -d bin $(find src -name "*.java")     # Windows PowerShell: see note below

# then run any example, e.g.:
java -cp bin dcj.examples.networking.DataStreamsDemo
java -cp bin dcj.examples.networking.PipedStreamExample
```

> **Windows PowerShell** doesn't have `find`. Compile with:
> `javac -d bin (Get-ChildItem -Recurse src -Filter *.java).FullName`
> or just let Eclipse/VS Code build it.

---

## How to run each example

Programs that need **two terminals** (a server and a client) are marked ⇄.

| Command | What it shows |
|---|---|
| `java -cp bin dcj.examples.networking.InetAddressDemo www.oreilly.com` | Resolve host names ↔ IP addresses |
| ⇄ `... EchoServer 5000` then `... EchoClient localhost 5000` | TCP `Socket`/`ServerSocket` round trip |
| ⇄ `... UdpReceiver 5000` then `... UdpSender localhost 5000 "hi"` | UDP datagrams |
| ⇄ `... MulticastReceiver 230.0.0.1 4446` then `... MulticastSender 230.0.0.1 4446 "hi group"` | One-to-many multicast |
| `java -cp bin dcj.examples.networking.PipedStreamExample` | Two threads talking over piped streams |
| `java -cp bin dcj.examples.networking.DataStreamsDemo` | Portable binary primitives |
| `java -cp bin dcj.examples.networking.UrlReader https://www.oreilly.com/` | Read a web page via `URL` (needs internet) |
| `java -cp bin dcj.examples.networking.UrlConnectionInfo https://www.oreilly.com/` | Inspect `URLConnection` headers (needs internet) |
| `java -cp bin dcj.util.FileClassLoader "dcj.examples.networking.DataStreamsDemo@bin/dcj/examples/networking/DataStreamsDemo.class"` | Custom `ClassLoader` loads a class from disk |

For the ⇄ pairs, start the server in one terminal, then run the client in a
second terminal.

---

## Your Assignment 1 (the `comp489.a1` package)

The scaffolds implement `Client ⇄ Proxy ⇄ Web Server`. They **compile and run**
but are intentionally **incomplete** — the graded logic is left to you as marked
`// TODO (assignment)` comments. Suggested order:

1. **`SimpleClient`** — get it sending a request and printing a response; test it
   against a real server first (`google.com` / port 80), *then* your proxy.
2. **`SimpleWebServer`** — replace the hard-coded 200 response with real file
   lookup: serve the file's **bytes** (so images work) or return `404`/`400`.
3. **`ProxyServer`** — parse the target host/port from the request and wire the
   two-way `relay()` between the client socket and the target socket.

> ⚠️ Per the Unit 4 rules: **do not post completed source/pseudocode** for the
> assignment to the discussion forum — share ideas and problem-solving steps only.
