$botDir = "D:\code\project-demo\youkeda_pj"
$logFile = "$botDir\bot_stdout.log"
$errFile = "$botDir\bot_err.log"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "java"
$psi.Arguments = "-jar $botDir\target\youkeda_project-0.0.1-SNAPSHOT.jar"
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.RedirectStandardInput = $true
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8

$p = [System.Diagnostics.Process]::Start($psi)

# Read output until "=== Claw" appears (bot is ready)
$ready = $false
$output = ""
while (-not $ready -and (-not $p.HasExited)) {
    $line = $p.StandardOutput.ReadLine()
    $output += $line + "`n"
    Write-Output $line
    if ($line -match "=== Claw") { $ready = $true }
}

# Send ilink login command
$p.StandardInput.WriteLine("ilink login")
Start-Sleep -Seconds 3

# Read QR code URL
$qrOutput = ""
for ($i = 0; $i -lt 30; $i++) {
    if ($p.HasExited) { break }
    $line = $p.StandardOutput.ReadLine()
    if ($line -eq $null) { break }
    $qrOutput += $line + "`n"
    Write-Output $line
}
if ($qrOutput -eq "") {
    # Try reading remaining output
    while (-not $p.StandardOutput.EndOfStream -and (-not $p.HasExited)) {
        $line = $p.StandardOutput.ReadLine()
        $qrOutput += $line + "`n"
        Write-Output $line
    }
}

# Keep running - read any further output
while ((-not $p.HasExited)) {
    $line = $p.StandardOutput.ReadLine()
    if ($line -eq $null) { break }
    Write-Output $line
}

$p.WaitForExit()
