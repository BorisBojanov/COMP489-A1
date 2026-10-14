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
│   └── comp489/a1/                ← YOUR Assignment 1 work
│       ├── SimpleClient.java           client: asks for a URL, talks to the proxy
│       ├── ProxyServer.java            proxy: finds the real destination, relays both ways
│       ├── SimpleWebServer.java        web server: serves files / 400 / 404
│       ├── MyHttp.java                 helper: parses an HTTP request or response
│       └── www/                        web server document root
│           └── test/testfile.html, testfile.jpg     files used for testing
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

javac -d bin .\src\comp489\a1\*.java
java -cp bin comp489.a1.SimpleWebServer   

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

```
Client  --(1)-->  ProxyServer  --(2)-->  SimpleWebServer
        <--(4)--               <--(3)--
```

1. The client sends `GET http://localhost:9806/test/testfile.html HTTP/1.0` to the proxy.
2. The proxy reads the request with `MyHttp`, takes the destination (host and port)
   from the absolute URL (or, failing that, the `Host:` header), opens a second
   socket to that server and forwards the request, rewritten to
   `GET /test/testfile.html HTTP/1.0`.
3. The web server answers with a status line, headers and the file's bytes.
4. The proxy relays the reply back to the client.

| Program | Default port | Arguments |
|---|---|---|
| `SimpleWebServer` | 9806 | `[port] [docRoot]` |
| `ProxyServer` | 8888 | `[listenPort]` |
| `SimpleClient` | connects to `localhost:8888` | `[proxyHost] [proxyPort]` |

### Step 1 – Compile

Open **PowerShell** in the project folder:

```powershell
cd C:\Users\boris\Documents\COMP489\COMP489-A1
mkdir bin -ErrorAction SilentlyContinue
javac -d bin (Get-ChildItem -Recurse src -Filter *.java).FullName
```

No output means success.

> **Always start the programs from the `COMP489-A1` folder** (the one that contains
> `src` and `bin`). `SimpleWebServer` looks for `src\comp489\a1\www` relative to
> the folder you start it from, and prints
> `Error: Document root is not a directory.` if it can't find it.
> In Eclipse: *Run Configurations → Arguments → Working directory → Default (the project)*.

### Step 2 – Start the three programs (three separate terminals)

All three are started from the `COMP489-A1` folder.

| Terminal | Command | You should see |
|---|---|---|
| 1 – web server | `java -cp bin comp489.a1.SimpleWebServer` | `SimpleWebServer listening on port 9806 ...` |
| 2 – proxy | `java -cp bin comp489.a1.ProxyServer` | `ProxyServer listening on port 8888 ...` |
| 3 – client | `java -cp bin comp489.a1.SimpleClient` | `Enter resource URL to request (...)` |

Start the web server first, then the proxy, then the client.

### Step 3 – Test `www\test\testfile.html`

The file `src\comp489\a1\www\test\testfile.html` is served at the path
**`/test/testfile.html`** because the web server's document root is the `www`
folder. The `www` folder itself never appears in the URL.

**A. Web server on its own (no proxy)** – open this in a browser:

```
http://localhost:9806/test/testfile.html
```

You should see the rendered page. If not, fix the web server before you look at the proxy.

**B. Through the proxy with curl** (`curl.exe` ships with Windows 10 and 11). In a
fourth PowerShell window:

```powershell
curl.exe -i -x http://localhost:8888 http://localhost:9806/test/testfile.html
```

Expected: `HTTP/1.0 200 OK`, `Content-Type: text/html`, `Content-Length: 1597`, then the HTML.

**C. Through the proxy with your own SimpleClient** – in Terminal 3, when it asks for a URL, type:

```
http://localhost:9806/test/testfile.html
```

Expected right now: the response header lines, `Headers read successfully.`,
`Content-Type: text/html` and `--- BODY (Reading 1597 bytes) ---`.
The client does not print the page text yet (see *Still to do*).

**Watch Terminal 1**: it should log `request: GET /test/testfile.html HTTP/1.0`.
If it logs the full `http://...` URL or `GET null`, the proxy's request rewriting is wrong.

### Test inputs for your test plan

| # | Input | Expected result |
|---|---|---|
| 1 | Browser: `http://localhost:9806/test/testfile.html` | Page displays (web server alone) |
| 2 | Browser: `http://localhost:9806/test/testfile.jpg` | Image displays |
| 3 | Client: `http://localhost:9806/test/testfile.html` | `200 OK`, `Content-Length: 1597` |
| 4 | `curl.exe -x http://localhost:8888 http://localhost:9806/test/testfile.jpg -o got.jpg` | `got.jpg` identical to the original (`fc.exe /b got.jpg src\comp489\a1\www\test\testfile.jpg`) |
| 5 | Client: `http://localhost:9806/test/nope.html` | `404 Not Found` |
| 6 | Web server directly, non-GET request | `400 Bad Request` |
| 7 | Client: a host that is not in `PROXY_HOSTNAMES` (e.g. `http://example.org/`) | Proxy logs `Access denied to host: ...` |

Record the **actual output** next to each expected result when you write the test plan
(.doc or .pdf, **not** .docx).

### Still to do

- **SimpleClient** only reads the body into memory. Print it (or save it) so the user sees the page,
  and handle replies with no `Content-Length`.
- **Allow-list** in `ProxyServer`: `www.google.com` is not in `PROXY_HOSTNAMES`, and the denied
  branch does not close the client socket, so the client waits until it times out. Check with your
  instructor whether an allow-list is wanted at all.
- **ftp://** (the assignment's `ftp://ftp.us.dell.com/readme.txt` test) is not handled; it is not HTTP.
- Remove debug `println` calls before submitting; add the required file/function headers; write the test plan.

> Per the Unit 4 rules: **do not post completed source/pseudocode** for the
> assignment to the discussion forum – share ideas and problem-solving steps only.
