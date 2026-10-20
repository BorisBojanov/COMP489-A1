# run_internet_tests.ps1 -- COMP 489 Assignment 1: instructor's internet tests I1 and I3
#
# Before running, start the web server and proxy (two terminals, from the COMP489-A1 folder):
#     java -cp bin comp489.a1.SimpleWebServer
#     java -cp bin comp489.a1.ProxyServer
# Then, from the COMP489-A1 folder:
#     powershell -ExecutionPolicy Bypass -File tests\run_internet_tests.ps1
#
# Copy the first lines of each result into the "Actual output" column of the test plan.

$tests = @(
    @{ Id = "I1"; Url = "https://www.google.com" },
    @{ Id = "I3"; Url = "http://www.google.com/" }
)

foreach ($t in $tests) {
    Write-Host ""
    Write-Host ("===== {0}: {1} =====" -f $t.Id, $t.Url)
    $start = Get-Date
    $out = $t.Url | java -cp bin comp489.a1.SimpleClient 2>&1
    $secs = ((Get-Date) - $start).TotalSeconds
    $out | Select-Object -First 12 | ForEach-Object { Write-Host $_ }
    Write-Host ("----- {0} lines of output, finished in {1:N1} s" -f $out.Count, $secs)
}
