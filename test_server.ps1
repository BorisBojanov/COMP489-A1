# Run the script in PowerShell
# .\test_server.ps1


Write-Host "=== Test 1: Root Index ===" -ForegroundColor Cyan
curl.exe -v http://localhost:9806/

Write-Host "`n`n=== Test 2: 404 Not Found ===" -ForegroundColor Cyan
curl.exe -v http://localhost:9806/nope.html

Write-Host "`n`n=== Test 3: Binary Image Download ===" -ForegroundColor Cyan
curl.exe -o out.jpg http://localhost:9806/picture.JPEG
Get-Item out.jpg | Select-Object Name, Length

Write-Host "`n`n=== Test 4: Directory Traversal Dot-Dot ===" -ForegroundColor Cyan
curl.exe -v --path-as-is 'http://localhost:9806/../SimpleWebServer.java'

Write-Host "`n`n=== Test 5: Encoded Directory Traversal ===" -ForegroundColor Cyan
curl.exe -v --path-as-is 'http://localhost:9806/%2e%2e/%2e%2e/README.md' 

Write-Host "`n`n=== Test 6: Malformed URL Escape (Server Survival Check) ===" -ForegroundColor Cyan
curl.exe -v 'http://localhost:9806/%zz' 

Write-Host "`n=== All tests sent. Check server logs to ensure it is still running! ===" -ForegroundColor Green
