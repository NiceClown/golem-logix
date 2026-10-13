param(
    [string]$ScriptPath = "tools/shots/playtest_audit.txt",
    [int]$Timeout = 360
)

$root = Split-Path -Parent $PSScriptRoot
$queue = Join-Path $root 'run\devshots\queue'
$glog = Join-Path $root 'run\devshots\gradle.log'

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return $env:JAVA_HOME }
    $c = Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) { return $c.FullName }
    throw 'JDK 21 not found: set JAVA_HOME'
}

function Find-Gradle {
    $local = Join-Path $root 'gradle-dist\gradle-8.10\bin\gradle.bat'
    if (Test-Path $local) { return $local }
    return (Join-Path $root 'gradlew.bat')
}

# 1. Clean up old processes
Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\.exe$' -and ($_.CommandLine -match 'golemlogix\.devshots' -or $_.CommandLine -match 'runDevShots' -or $_.CommandLine -match 'devShotsRun') } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

New-Item -ItemType Directory -Force -Path $queue | Out-Null
Remove-Item -LiteralPath $glog -ErrorAction SilentlyContinue

$name = [IO.Path]::GetFileNameWithoutExtension($ScriptPath)
Remove-Item -LiteralPath (Join-Path $queue "$name.done") -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $queue "$name.log") -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $queue "$name.txt") -ErrorAction SilentlyContinue

# 2. Put script into queue
Copy-Item -LiteralPath $ScriptPath -Destination (Join-Path $queue "$name.txt") -Force

# 3. Launch Gradle in background
$jdk = Find-Jdk
$gradle = Find-Gradle
$cmd = "set `"JAVA_HOME=$jdk`" && cd /d `"$root`" && call `"$gradle`" runDevShots --console=plain > `"$glog`" 2>&1"
$proc = Start-Process -FilePath cmd.exe -ArgumentList "/c $cmd" -WindowStyle Minimized -PassThru

Write-Host "Started client, waiting for READY and execution of $name..."
$t0 = Get-Date
$donePath = Join-Path $queue "$name.done"
$logPath = Join-Path $queue "$name.log"

while (((Get-Date) - $t0).TotalSeconds -lt $Timeout) {
    Start-Sleep -Seconds 3
    if (Test-Path $donePath) {
        Write-Host "SCRIPT FINISHED in $([int]((Get-Date) - $t0).TotalSeconds) s"
        break
    }
    if (Test-Path $glog) {
        $txt = Get-Content $glog -Raw -ErrorAction SilentlyContinue
        if ($txt -match 'BUILD FAILED') {
            Write-Host "BUILD FAILED!"
            ($txt -split "`n" | Select-Object -Last 30) -join "`n"
            break
        }
    }
}

if (Test-Path $logPath) {
    Write-Host "--- LOG OUTPUT ---"
    Get-Content $logPath
} else {
    Write-Host "No log generated yet."
}

# Stop client cleanly
Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\.exe$' -and ($_.CommandLine -match 'golemlogix\.devshots' -or $_.CommandLine -match 'runDevShots' -or $_.CommandLine -match 'devShotsRun') } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
