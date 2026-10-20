#!/usr/bin/env python3
"""Usage (from the COMP489-A1 folder):  python tests/run_tests.py
Needs Java (javac + java) and Python 3; compiles into ./bin, uses ports 7777-7779, 8888-8889, 9806-9807.

Automated test runner for COMP 489 Assignment 1 (SimpleWebServer, ProxyServer, SimpleClient).
Starts the programs as real processes and talks to them over real TCP sockets."""
import hashlib, json, os, socket, subprocess, sys, threading, time, concurrent.futures as cf

BASE = os.path.dirname(os.path.abspath(__file__))
# Run from <project>/tests (default) or from a folder containing a copy in ./proj
PROJ = os.path.join(BASE, "proj") if os.path.isdir(os.path.join(BASE, "proj")) else os.path.dirname(BASE)
OUT = os.path.join(PROJ, "bin")
WWW = os.path.join(PROJ, "src", "comp489", "a1", "www")
ENV = dict(os.environ, JAVA_TOOL_OPTIONS="")
results = []
procs = []

def add(tid, area, item, inp, expected, actual, ok, remark=""):
    results.append(dict(id=tid, area=area, item=item, input=inp, expected=expected,
                        actual=actual, result=("Pass" if ok is True else "Fail" if ok is False else ok),
                        remark=remark))
    print(f"{tid:5} {results[-1]['result']:6} {item}")

def sha(b): return hashlib.sha256(b).hexdigest()[:16]
def fbytes(rel): return open(os.path.join(WWW, rel), "rb").read()

def start(cls, args=(), cwd=PROJ, log=None):
    f = open(os.path.join(BASE, log), "w") if log else subprocess.DEVNULL
    p = subprocess.Popen(["java", "-cp", OUT, f"comp489.a1.{cls}", *map(str, args)],
                         cwd=cwd, stdout=f, stderr=subprocess.STDOUT, env=ENV)
    procs.append(p); return p

def wait_port(port, t=10):
    end = time.time() + t
    while time.time() < end:
        try: socket.create_connection(("127.0.0.1", port), 0.3).close(); return True
        except OSError: time.sleep(0.2)
    return False

def raw(port, data, timeout=10, half_close=False):
    s = socket.create_connection(("127.0.0.1", port), timeout)
    s.settimeout(timeout); s.sendall(data)
    if half_close: s.shutdown(socket.SHUT_WR)
    buf = b""
    try:
        while True:
            c = s.recv(65536)
            if not c: break
            buf += c
    except socket.timeout:
        buf += b"<<TIMEOUT>>"
    s.close(); return buf

def parse(resp):
    head, _, body = resp.partition(b"\r\n\r\n")
    lines = head.decode("latin-1").split("\r\n")
    hdr = {}
    for l in lines[1:]:
        if ":" in l: k, v = l.split(":", 1); hdr[k.strip().lower()] = v.strip()
    return lines[0], hdr, body

def client(text, args=(), timeout=30):
    t0 = time.time()
    p = subprocess.run(["java", "-cp", OUT, "comp489.a1.SimpleClient", *map(str, args)],
                       input=(text + "\n").encode(), capture_output=True, timeout=timeout, cwd=PROJ, env=ENV)
    out = p.stdout.replace(b"Enter resource URL to request (e.g. http://example.com/page.html): ", b"")
    return out, time.time() - t0

def first(out, n=3):
    lines = [l for l in out.decode("latin-1").splitlines() if l.strip() and not l.startswith("Picked up")]
    return " | ".join(lines[:n])

def log(name): return "".join(l for l in open(os.path.join(BASE, name)) if not l.startswith("Picked up"))

# ---------- fake servers for edge cases ----------
def fake_no_length(port, ready):
    s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", port)); s.listen(); ready.set()
    while True:
        c, _ = s.accept(); c.recv(4096)
        c.sendall(b"HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\n\r\nNo Content-Length here.\nSecond line.\n")
        c.close()

def fake_silent(port, ready):
    s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", port)); s.listen(); ready.set()
    held = []
    while True:
        c, _ = s.accept(); held.append(c)   # accept, then never answer

def bg(fn, port):
    e = threading.Event(); threading.Thread(target=fn, args=(port, e), daemon=True).start(); e.wait()

# =====================================================================
def main():
    html, jpg, pic = fbytes("test/testfile.html"), fbytes("test/testfile.jpg"), fbytes("picture.JPEG")

    # ---------------- 1. Compilation ----------------
    for tid, rel in (("C1", None), ("C2", "11")):
        cmd = ["javac", "-Xlint:all", "-d", OUT] + (["--release", rel] if rel else []) + \
              [os.path.join(PROJ, "src", "comp489", "a1", f) for f in
               ("SimpleWebServer.java", "ProxyServer.java", "SimpleClient.java", "MyHttp.java")]
        p = subprocess.run(cmd, capture_output=True, text=True, env=ENV)
        warns = [l for l in p.stderr.splitlines() if "warning" in l]
        add(tid, "Compile", "Compile all four source files from one directory" + (" (Java 11 target)" if rel else ""),
            "javac -d bin src/comp489/a1/*.java" + (" --release 11" if rel else ""),
            "No compile errors; 4 classes produced",
            f"exit code {p.returncode}; {len(warns)} warning(s); classes: " +
            ", ".join(sorted(f for f in os.listdir(os.path.join(OUT, "comp489", "a1")) if f.endswith(".class") and "$" not in f)),
            p.returncode == 0, "; ".join(w.split("warning:")[-1].strip() for w in warns[:3]))

    # ---------------- 2. Web server start-up ----------------
    p = subprocess.run(["java", "-cp", OUT, "comp489.a1.SimpleWebServer"], cwd=BASE,
                       capture_output=True, text=True, timeout=10, env=ENV)
    add("W0", "Web server", "Start-up check: document root missing",
        "Start SimpleWebServer from a folder that has no src/comp489/a1/www",
        "Prints 'Document root is not a directory' and exits cleanly",
        first(p.stdout.encode(), 2), "not a directory" in p.stdout and p.returncode == 0)

    start("SimpleWebServer", log="ws.log"); assert wait_port(9806)
    start("ProxyServer", log="px.log"); assert wait_port(8888)
    time.sleep(0.5)
    add("W1", "Web server", "Start-up with defaults", "java comp489.a1.SimpleWebServer (from project folder)",
        "Prints document root and 'listening on port 9806'", first(log("ws.log").encode(), 2),
        "listening on port 9806" in log("ws.log"))

    # ---------------- 3. Web server direct ----------------
    def ws(tid, item, req, exp, check, remark="", port=9806):
        r = raw(port, req.encode()); st, h, b = parse(r)
        ok, act = check(st, h, b)
        add(tid, "Web server", item, req.replace("\r\n", "\\r\\n"), exp, act, ok, remark)
        return r

    ws("W2", "Serve HTML file (testfile.html)", "GET /test/testfile.html HTTP/1.0\r\n\r\n",
       "HTTP/1.0 200 OK, Content-Type text/html, Content-Length 1597, body identical to file",
       lambda st, h, b: (st == "HTTP/1.0 200 OK" and h.get("content-type") == "text/html" and b == html,
                         f"{st}; Content-Type {h.get('content-type')}; Content-Length {h.get('content-length')}; body {len(b)} bytes, identical={b == html}"))
    ws("W3", "Serve JPEG file byte-for-byte (testfile.jpg)", "GET /test/testfile.jpg HTTP/1.0\r\n\r\n",
       "200 OK, image/jpeg, 1961523 bytes, SHA-256 identical to file on disk",
       lambda st, h, b: (st.endswith("200 OK") and h.get("content-type") == "image/jpeg" and b == jpg,
                         f"{st}; {h.get('content-type')}; {len(b)} bytes; sha256 {sha(b)} vs file {sha(jpg)}"))
    ws("W4", "Serve large binary (3.1 MB picture.JPEG)", "GET /picture.JPEG HTTP/1.0\r\n\r\n",
       "200 OK, 3187528 bytes, identical to file",
       lambda st, h, b: (st.endswith("200 OK") and b == pic,
                         f"{st}; Content-Type {h.get('content-type')}; {len(b)} bytes; identical={b == pic}"))
    ws("W5", "Default document for '/'", "GET / HTTP/1.0\r\n\r\n",
       "200 OK with the contents of www/index.html",
       lambda st, h, b: (st.endswith("200 OK"), f"{st}; Content-Length {h.get('content-length')}; body {len(b)} bytes"),
       remark="index.html is currently empty (0 bytes); add content before submitting")
    ws("W6", "Query string is ignored", "GET /test/testfile.html?lang=en HTTP/1.0\r\n\r\n",
       "200 OK, same 1597-byte file",
       lambda st, h, b: (st.endswith("200 OK") and b == html, f"{st}; body {len(b)} bytes, identical={b == html}"))
    ws("W7", "Missing file returns 404", "GET /test/nope.html HTTP/1.0\r\n\r\n",
       "HTTP/1.0 404 Not Found with a short message",
       lambda st, h, b: (st == "HTTP/1.0 404 Not Found", f"{st}; body: " + b.decode().replace(PROJ, "<project>")[:120]),
       remark="Message reveals the server's full disk path; consider 'File not found: /test/nope.html'")
    ws("W8", "Directory requested without trailing '/'", "GET /test HTTP/1.0\r\n\r\n",
       "404 Not Found (a directory is not a file)",
       lambda st, h, b: (st.endswith("404 Not Found"), st))
    ws("W9", "Non-GET method rejected (POST)", "POST /test/testfile.html HTTP/1.0\r\nContent-Length: 0\r\n\r\n",
       "HTTP/1.0 400 Bad Request",
       lambda st, h, b: (st == "HTTP/1.0 400 Bad Request", f"{st}; body: {b.decode()}"))
    ws("W10", "Non-GET method rejected (HEAD)", "HEAD / HTTP/1.0\r\n\r\n", "HTTP/1.0 400 Bad Request",
       lambda st, h, b: (st.endswith("400 Bad Request"), st))
    ws("W11", "Path traversal with '..' blocked", "GET /../SimpleWebServer.java HTTP/1.0\r\n\r\n",
       "404 (Forbidden); source code NOT returned",
       lambda st, h, b: (st.endswith("404 Not Found") and b"class SimpleWebServer" not in b, f"{st}; body: {b.decode()[:60]}"))
    ws("W12", "URL-encoded traversal (%2e%2e) blocked", "GET /%2e%2e/%2e%2e/%2e%2e/%2e%2e/README.md HTTP/1.0\r\n\r\n",
       "404 (Forbidden); README.md NOT returned",
       lambda st, h, b: (st.endswith("404 Not Found") and len(b) < 200, f"{st}; body: {b.decode()[:60]}"))
    ws("W13", "Absolute-path escape ('//etc/passwd') blocked", "GET //etc/passwd HTTP/1.0\r\n\r\n",
       "404 (Forbidden); system file NOT returned",
       lambda st, h, b: (st.endswith("404 Not Found") and b"root:" not in b, f"{st}; body: {b.decode()[:60]}"),
       remark="Windows equivalent: GET /C:/Windows/win.ini")
    ws("W14", "Malformed percent-escape", "GET /%zz HTTP/1.0\r\n\r\n", "HTTP/1.0 400 Bad Request; server keeps running",
       lambda st, h, b: (st.endswith("400 Bad Request"), f"{st}; body: {b.decode()}"))
    # empty connection
    s = socket.create_connection(("127.0.0.1", 9806)); s.close(); time.sleep(0.3)
    r = raw(9806, b"GET /test/testfile.html HTTP/1.0\r\n\r\n")
    add("W15", "Web server", "Server survives a client that connects and sends nothing", "Open a TCP connection, close it, then GET /test/testfile.html",
        "No crash; next request still gets 200 OK", parse(r)[0] + " on the follow-up request", parse(r)[0].endswith("200 OK"))
    # concurrency
    def get_jpg(_): return raw(9806, b"GET /test/testfile.jpg HTTP/1.0\r\n\r\n")
    t0 = time.time()
    with cf.ThreadPoolExecutor(10) as ex: rs = list(ex.map(get_jpg, range(10)))
    good = sum(parse(x)[2] == jpg for x in rs)
    add("W16", "Web server", "Concurrent clients (one thread per connection)", "10 simultaneous GET /test/testfile.jpg",
        "All 10 receive the complete, identical image", f"{good}/10 identical in {time.time()-t0:.2f}s", good == 10)
    # alternate port + docroot args
    start("SimpleWebServer", args=(9807, os.path.join(WWW, "test")), log="ws2.log"); wait_port(9807)
    r = raw(9807, b"GET /testfile.html HTTP/1.0\r\n\r\n"); st, h, b = parse(r)
    add("W17", "Web server", "Command-line arguments: port and document root", "java SimpleWebServer 9807 <path>/www/test ; GET /testfile.html",
        "Listens on 9807 and serves from the given folder", f"{st}; body identical={b == html}", st.endswith("200 OK") and b == html)

    # ---------------- 4. Proxy ----------------
    def px(tid, item, req, exp, check, remark="", half=False):
        r = raw(8888, req.encode(), half_close=half); time.sleep(0.4); st, h, b = parse(r)
        ok, act = check(r, st, h, b)
        add(tid, "Proxy", item, req.replace("\r\n", "\\r\\n"), exp, act, ok, remark)

    add("P1", "Proxy", "Start-up with defaults", "java comp489.a1.ProxyServer", "Prints 'listening on port 8888'",
        first(log("px.log").encode(), 1), "listening on port 8888" in log("px.log"))
    before = log("ws.log")
    px("P2", "Forward absolute-URL request (HTML)", "GET http://localhost:9806/test/testfile.html HTTP/1.0\r\nHost: localhost:9806\r\n\r\n",
       "200 OK and the identical 1597-byte file relayed back",
       lambda r, st, h, b: (st.endswith("200 OK") and b == html, f"{st}; {len(b)} bytes; identical={b == html}"))
    time.sleep(0.3); new = log("ws.log")[len(before):]
    add("P3", "Proxy", "Request line is rewritten for the web server", "(same request as P2)",
        "Web server log shows 'GET /test/testfile.html HTTP/1.0' (path only, no http://)",
        next((l for l in new.splitlines() if l.startswith("request:")), "(nothing logged)"),
        "request: GET /test/testfile.html HTTP/1.0" in new)
    px("P4", "Forward binary (JPEG) byte-for-byte", "GET http://localhost:9806/test/testfile.jpg HTTP/1.0\r\n\r\n",
       "1961523 bytes, SHA-256 identical to file",
       lambda r, st, h, b: (b == jpg, f"{st}; {len(b)} bytes; sha256 {sha(b)} vs {sha(jpg)}"))
    px("P5", "Instructor form: http://127.0.0.1/... ", "GET http://127.0.0.1:9806/test/testfile.html HTTP/1.0\r\n\r\n",
       "200 OK, identical file (127.0.0.1 is allowed)",
       lambda r, st, h, b: (b == html, f"{st}; identical={b == html}"),
       remark="The spec's http://127.0.0.1/testfile.html uses port 80; our server runs on 9806")
    px("P6", "Origin-form request + Host header", "GET /test/testfile.html HTTP/1.0\r\nHost: localhost:9806\r\n\r\n",
       "Proxy takes host/port from Host: header; 200 OK",
       lambda r, st, h, b: (b == html, f"{st}; identical={b == html}"))
    px("P7", "404 relayed from web server", "GET http://localhost:9806/missing.html HTTP/1.0\r\n\r\n",
       "HTTP/1.0 404 Not Found relayed unchanged",
       lambda r, st, h, b: (st.endswith("404 Not Found"), st))
    px("P8", "400 relayed for non-GET", "POST http://localhost:9806/test/testfile.html HTTP/1.0\r\nContent-Length: 5\r\n\r\nhello",
       "HTTP/1.0 400 Bad Request relayed (proxy forwards the body using Content-Length)",
       lambda r, st, h, b: (st.endswith("400 Bad Request"), st))
    bg(fake_no_length, 7779)
    px("P9", "Response with no Content-Length (reads to end of stream)", "GET http://localhost:7779/x HTTP/1.0\r\n\r\n",
       "Whole body relayed; connection then closed (no hang)",
       lambda r, st, h, b: (b"Second line." in b and b"<<TIMEOUT>>" not in r, f"{st}; body {b!r}"),
       remark="Test server on port 7779 that sends no Content-Length, like www.google.com")
    t0 = time.time()
    px("P10", "Client keeps its side open after the request (half-close test)", "GET http://localhost:9806/test/testfile.html HTTP/1.0\r\n\r\n  (client never half-closes)",
       "Full response delivered, then proxy closes the connection without waiting",
       lambda r, st, h, b: (b == html and b"<<TIMEOUT>>" not in r, f"{st}; identical={b == html}; finished in {time.time()-t0:.2f}s"))
    before = log("px.log")
    px("P11", "Host not on the allow-list is refused", "GET http://example.org/ HTTP/1.0\r\n\r\n",
       "Proxy logs 'Access denied to host: example.org' and closes the connection",
       lambda r, st, h, b: (r == b"" and "Access denied to host: example.org" in log("px.log")[len(before):] if (time.sleep(0.3) or True) else False,
                            f"client received {len(r)} bytes; proxy log: " + (log("px.log")[len(before):].strip().splitlines() or ["-"])[-1]),
       remark="Connection is closed without an HTTP error reply")
    px("P12", "Invalid port rejected", "GET http://localhost:99999/ HTTP/1.0\r\n\r\n",
       "Proxy logs 'Invalid target host or port' and closes the connection",
       lambda r, st, h, b: (r == b"" and "Invalid target" in log("px.log"), f"client received {len(r)} bytes"))
    px("P13", "Target web server not running", "GET http://localhost:9899/ HTTP/1.0\r\n\r\n",
       "Connection closed (or an error reply); proxy keeps running",
       lambda r, st, h, b: (b"<<TIMEOUT>>" not in r, f"client received {len(r)} bytes; proxy log: " + log("px.log").strip().splitlines()[-1]),
       remark="Closed without an HTTP error reply (e.g. 502 Bad Gateway)")
    px("P14", "Garbage request line", "HELLO\r\n\r\n", "Connection closed; proxy keeps running",
       lambda r, st, h, b: (b"<<TIMEOUT>>" not in r,
                            "No reply; connection still open after 10 s (proxy thread blocked waiting for the client)"
                            if b"<<TIMEOUT>>" in r else f"connection closed; client received {len(r)} bytes"))
    def via_proxy(_): return raw(8888, b"GET http://localhost:9806/test/testfile.jpg HTTP/1.0\r\n\r\n", timeout=20)
    t0 = time.time()
    with cf.ThreadPoolExecutor(10) as ex: rs = list(ex.map(via_proxy, range(10)))
    good = sum(parse(x)[2] == jpg for x in rs)
    add("P15", "Proxy", "Concurrent clients through the proxy (threads)", "10 simultaneous JPEG requests via the proxy",
        "All 10 complete and identical", f"{good}/10 identical in {time.time()-t0:.2f}s", good == 10)
    wait_port(8888)
    r = raw(8888, b"GET http://localhost:9806/test/testfile.html HTTP/1.0\r\n\r\n")
    add("P16", "Proxy", "Proxy still healthy after all error cases", "GET testfile.html after P11-P15",
        "200 OK", parse(r)[0], parse(r)[0].endswith("200 OK"))
    start("ProxyServer", args=(8889,), log="px2.log"); wait_port(8889)
    out, _ = client("http://localhost:9806/test/testfile.html", args=("localhost", 8889))
    add("P17", "Proxy", "Command-line argument: listen port", "java ProxyServer 8889 ; SimpleClient localhost 8889",
        "Proxy listens on 8889; client gets 200 OK", first(out, 1), b"200 OK" in out)

    # ---------------- 5. Client ----------------
    def cl(tid, item, text, exp, check, args=(), remark=""):
        out, dt = client(text, args)
        ok, act = check(out, dt)
        add(tid, "Client", item, (text if text else "(press Enter)") + (f"   [args: {' '.join(map(str,args))}]" if args else ""),
            exp, act, ok, remark)
    cl("CL1", "Fetch HTML via proxy and print it", "http://localhost:9806/test/testfile.html",
       "Prints status line, headers, then the HTML text",
       lambda o, dt: (b"HTTP/1.0 200 OK" in o and html.strip() in o, first(o, 3) + f" ... HTML body printed={html.strip() in o}"))
    cl("CL2", "Fetch JPEG via proxy", "http://localhost:9806/test/testfile.jpg",
       "200 OK, image/jpeg, Content-Length 1961523; raw bytes printed",
       lambda o, dt: (b"200 OK" in o and jpg in o, first(o, 3) + f"; all {len(jpg)} image bytes printed={jpg in o}"),
       remark="Spec: client only prints what it receives; binary shows as unreadable characters")
    cl("CL3", "Missing resource", "http://localhost:9806/test/nope.html", "Prints 'HTTP/1.0 404 Not Found' and the message",
       lambda o, dt: (b"404 Not Found" in o, first(o, 1)))
    cl("CL4", "Blank input uses the default URL", "", "Uses http://localhost:9806/ and prints the response",
       lambda o, dt: (b"using default: http://localhost:9806/" in o and b"200 OK" in o, first(o, 2)))
    cl("CL5", "Bare path rejected", "/index.html", "Asks for a full URL", lambda o, dt: (b"Please enter a full URL" in o, first(o, 1)))
    cl("CL6", "Malformed URL rejected", "http://bad url", "'Not a valid URL' message, no crash",
       lambda o, dt: (b"Not a valid URL" in o, first(o, 1)))
    cl("CL7", "Unsupported scheme (ftp://)", "ftp://ftp.us.dell.com/readme.txt", "Explains only http/https are supported",
       lambda o, dt: (b"Unsupported scheme" in o, first(o, 1)),
       remark="FTP is not HTTP; this proxy forwards HTTP only (see I2)")
    cl("CL8", "Response with no Content-Length", "http://localhost:7779/x", "Whole body printed; client exits normally",
       lambda o, dt: (b"Second line." in o and dt < 6, first(o, 4) + f"; took {dt:.1f}s"))
    cl("CL9", "Host not allowed by proxy", "http://example.org/", "Clear message that no response was received",
       lambda o, dt: (b"null" not in o and len(o.strip()) > 0, repr(o.decode().strip()[:80])))
    cl("CL10", "Proxy not running", "http://localhost:9806/", "'Client error: Connection refused (is the proxy running ...?)'",
       lambda o, dt: (b"is the proxy running" in o, first(o, 1)), args=("localhost", 8899))
    bg(fake_silent, 7777)
    cl("CL11", "Proxy accepts but never answers (SO_TIMEOUT)", "http://localhost:9806/", "Timeout message after about 7 s, no hang",
       lambda o, dt: (b"Timed out" in o and dt < 12, first(o, 1) + f" (after {dt:.1f}s)"), args=("localhost", 7777))
    cl("CL12", "URL with query string", "http://localhost:9806/test/testfile.html?x=1&y=2", "200 OK, HTML printed",
       lambda o, dt: (b"200 OK" in o and html.strip() in o, first(o, 1)))

    # ---------------- 6. Internet (instructor's) tests: need real internet ----------------
    out, _ = client("ftp://ftp.us.dell.com/readme.txt")
    add("I2", "Internet", "Instructor test: ftp://ftp.us.dell.com/readme.txt", "ftp://ftp.us.dell.com/readme.txt",
        "Handled gracefully (FTP is a different protocol from HTTP)", first(out, 1),
        "Limitation" if b"Unsupported scheme" in out else False,
        "Client rejects ftp:// before contacting the proxy; the proxy forwards HTTP only. No crash.")
    for tid, inp, exp in (("I1", "https://www.google.com", "Proxy contacts www.google.com:443 and the client reports the outcome clearly"),
                          ("I3", "http://www.google.com/", "200 OK (or 301) and Google's HTML printed; no Content-Length, read to end of stream")):
        add(tid, "Internet", "Instructor test: " + inp, inp, exp,
            "Not run here: the test environment cannot reach www.google.com",
            "Run on PC", "Run run_internet_tests.ps1 on your PC and fill in the actual result")

def cleanup():
    for p in procs:
        try: p.terminate()
        except Exception: pass

try:
    main()
finally:
    cleanup()
    json.dump(results, open(os.path.join(BASE, "results.json"), "w"), indent=1)
    print("PASS", sum(r["result"] == "Pass" for r in results), "FAIL", sum(r["result"] == "Fail" for r in results),
          "OTHER", sum(r["result"] not in ("Pass", "Fail") for r in results))
