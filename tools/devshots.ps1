# Golem Logix DevShots Helper (gradle runDevShots)
param(
    [Parameter(Position = 0)][string]$Action = 'status',
    [Parameter(Position = 1)][string]$Arg = '',
    [int]$Timeout = 180,
    [switch]$Mods,
    [string]$Server = '',
    [string]$GradleArgs = ''
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

function Get-ClientProcs {
    Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\.exe$' -and ($_.CommandLine -match 'golemlogix\.devshots' -or $_.CommandLine -match 'runDevShots' -or $_.CommandLine -match 'devShotsRun') }
}

switch ($Action) {
    'start' {
        if (Get-ClientProcs) { 'client already running'; break }
        New-Item -ItemType Directory -Force -Path $queue | Out-Null
        Remove-Item -LiteralPath $glog -ErrorAction SilentlyContinue
        $jdk = Find-Jdk
        $gradle = Find-Gradle
        $extra = if ($Mods) { '-PtestMods ' } else { '' }
        if ($GradleArgs) { $extra += "$GradleArgs " }
        if ($Server) { $extra += "-PdevshotsServer=$Server " }
        $cmd = "set `"JAVA_HOME=$jdk`" && cd /d `"$root`" && call `"$gradle`" $($extra)runDevShots --console=plain > `"$glog`" 2>&1"
        Start-Process -FilePath cmd.exe -ArgumentList "/c $cmd" -WindowStyle Minimized
        $t0 = Get-Date
        while (((Get-Date) - $t0).TotalSeconds -lt $Timeout) {
            Start-Sleep -Seconds 3
            if (Test-Path $glog) {
                $txt = Get-Content $glog -Raw -ErrorAction SilentlyContinue
                if ($txt -match '\[DevShots\] READY') { 'ready in {0:N0} s' -f ((Get-Date) - $t0).TotalSeconds; break }
                if ($txt -match 'BUILD FAILED') { 'BUILD FAILED'; ($txt -split "`n" | Select-Object -Last 40) -join "`n"; break }
            }
        }
    }
    'wait' {
        $t0 = Get-Date
        $done = Join-Path $queue "$Arg.done"
        $log = Join-Path $queue "$Arg.log"
        while (((Get-Date) - $t0).TotalSeconds -lt $Timeout) {
            if (Test-Path $done) { break }
            Start-Sleep -Seconds 2
        }
        if (Test-Path $log) { Get-Content $log } else { 'no log yet' }
    }
    'run' {
        $name = [IO.Path]::GetFileNameWithoutExtension($Arg)
        Remove-Item -LiteralPath (Join-Path $queue "$name.done") -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $queue "$name.log") -ErrorAction SilentlyContinue
        Copy-Item -LiteralPath $Arg -Destination (Join-Path $queue "$name.txt") -Force
        & $PSCommandPath wait $name -Timeout $Timeout
    }
    'stop' {
        Get-ClientProcs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
        'stopped'
    }
    'server' {
        $slog = Join-Path $root 'run\server\gradle.log'
        if (Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'forgeserver' }) { 'server already running'; break }
        $t1 = Get-Date
        while ((Test-Path $slog) -and ((Get-Date) - $t1).TotalSeconds -lt 30) {
            Remove-Item -LiteralPath $slog -ErrorAction SilentlyContinue
            if (Test-Path $slog) { Start-Sleep -Seconds 1 }
        }
        if (Test-Path $slog) { 'server log is busy'; break }
        $jdk = Find-Jdk
        $gradle = Find-Gradle
        $extra = if ($Mods) { '-PtestMods ' } else { '' }
        $cmd = "set `"JAVA_HOME=$jdk`" && cd /d `"$root`" && call `"$gradle`" $($extra)runServer --console=plain > `"$slog`" 2>&1"
        Start-Process -FilePath cmd.exe -ArgumentList "/c $cmd" -WindowStyle Minimized
        $t0 = Get-Date
        while (((Get-Date) - $t0).TotalSeconds -lt $Timeout) {
            Start-Sleep -Seconds 3
            if (Test-Path $slog) {
                $txt = Get-Content $slog -Raw -ErrorAction SilentlyContinue
                if ($txt -match 'Done \(') { 'server ready in {0:N0} s' -f ((Get-Date) - $t0).TotalSeconds; break }
                if ($txt -match 'BUILD FAILED|Exception in server tick loop|Failed to start') { 'SERVER FAILED'; ($txt -split "`n" | Select-Object -Last 40) -join "`n"; break }
            }
        }
    }
    'serverstop' {
        Get-CimInstance Win32_Process | Where-Object { ($_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'forgeserver') -or
                ($_.Name -match '^(cmd|java)\.exe$' -and $_.CommandLine -match 'runServer') } |
                ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        'server stopped'
    }
    'art' {
        $jdk = Find-Jdk
        $out = Join-Path $env:TEMP 'golemlogix-toolsout'
        if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $out | Out-Null
        Push-Location $root
        $files = Get-ChildItem tools -Filter *.java | Where-Object { $_.Name -notin 'GeneratePdfDocument.java', 'Audit.java' } | ForEach-Object { $_.FullName }
        & "$jdk\bin\javac.exe" -encoding UTF-8 -d $out $files
        if ($LASTEXITCODE -eq 0) { & "$jdk\bin\java.exe" '-Dfile.encoding=UTF-8' -cp $out ArtForge }
        Pop-Location
    }
    'audit' {
        $jdk = Find-Jdk
        Push-Location $root
        & "$jdk\bin\java.exe" '-Dfile.encoding=UTF-8' tools\Audit.java
        Pop-Location
    }
    default {
        $p = Get-ClientProcs
        if ($p) { 'client running, pid ' + (($p | ForEach-Object { $_.ProcessId }) -join ',') } else { 'client not running' }
        if (Test-Path $queue) { Get-ChildItem $queue | Select-Object -ExpandProperty Name }
    }
}
