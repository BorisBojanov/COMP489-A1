#!/bin/bash
# terminal and give the file execution permissions:
#       chmod +x test_server.sh
# macOS, Linux, WSL, and Git Bash/Cygwin on Windows
# Run the script in a terminal with:
#   ./test_server.sh

echo "=== Test 1: Root Index ==="
curl -v http://localhost:9806/
echo -e "\n\n=== Test 2: 404 Not Found ==="
curl -v http://localhost:9806/nope.html
echo -e "\n\n=== Test 3: Binary Image Download ==="
curl -o out.jpg http://localhost:9806/picture.JPEG
ls -l out.jpg 2>/dev/null || wc -c out.jpg
echo -e "\n\n=== Test 4: Directory Traversal Dot-Dot ==="
curl -v --path-as-is 'http://localhost:9806/../SimpleWebServer.java'
echo -e "\n\n=== Test 5: Encoded Directory Traversal ==="
curl -v --path-as-is 'http://localhost:9806/%2e%2e/%2e%2e/README.md'
echo -e "\n\n=== Test 6: Malformed URL Escape (Server Survival Check) ==="
curl -v 'http://localhost:9806/%zz'

echo -e "\n=== All tests sent. Check server logs to ensure it is still running! ==="
