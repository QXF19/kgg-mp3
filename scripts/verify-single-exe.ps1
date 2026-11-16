$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$TestRoot = Join-Path $ProjectRoot ('build\single-exe-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $TestRoot | Out-Null
$Exe = Join-Path $TestRoot 'kgg-mp3.exe'
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'dist\kgg-mp3.exe') -Destination $Exe
$OriginalLocal = $env:LOCALAPPDATA
$OriginalPath = $env:PATH
$OriginalJava = $env:JAVA_HOME
Push-Location $TestRoot
try {
    $env:LOCALAPPDATA = Join-Path $TestRoot 'profile'
    $env:PATH = "$env:WINDIR\System32;$env:WINDIR;$env:WINDIR\System32\WindowsPowerShell\v1.0"
    $env:JAVA_HOME = ''
    # Two simultaneous first launches must share one complete cache.
    $first = Start-Process -FilePath $Exe -WorkingDirectory $TestRoot -ArgumentList '--diagnose' -PassThru -RedirectStandardOutput (Join-Path $TestRoot 'cold.txt') -RedirectStandardError (Join-Path $TestRoot 'cold-error.txt')
    $null = $first.Handle
    $second = Start-Process -FilePath $Exe -WorkingDirectory $TestRoot -ArgumentList '--diagnose' -PassThru -RedirectStandardOutput (Join-Path $TestRoot 'concurrent.txt') -RedirectStandardError (Join-Path $TestRoot 'concurrent-error.txt')
    $null = $second.Handle
    foreach ($process in @($first, $second)) {
        if (-not $process.WaitForExit(60000)) { throw 'Cold launch timed out' }
        $process.Refresh()
        if ($process.ExitCode -ne 0) { throw "Cold launch failed: $($process.ExitCode)" }
    }
    $diagnostic = Get-Content -LiteralPath (Join-Path $TestRoot 'cold.txt') -Raw
    if ($diagnostic -match '(ffmpeg|ffprobe|sqlite3)=NOT_FOUND' -or $diagnostic -notmatch [regex]::Escape($env:LOCALAPPDATA)) { throw "Missing bundled dependencies: $diagnostic" }
    $cache = @(Get-ChildItem -LiteralPath (Join-Path $env:LOCALAPPDATA 'kgg-mp3\runtime') -Directory)
    if ($cache.Count -ne 1) { throw 'Concurrent extraction created multiple caches' }
    $marker = Get-Item -LiteralPath (Join-Path $cache[0].FullName 'complete')
    $stamp = $marker.LastWriteTimeUtc
    $warm = Start-Process -FilePath $Exe -WorkingDirectory $TestRoot -ArgumentList '--diagnose' -PassThru -Wait -RedirectStandardOutput (Join-Path $TestRoot 'warm.txt') -RedirectStandardError (Join-Path $TestRoot 'warm-error.txt')
    if ($warm.ExitCode -ne 0 -or (Get-Item -LiteralPath $marker.FullName).LastWriteTimeUtc -ne $stamp) { throw 'Warm launch failed to reuse the cache' }
    Write-Host 'PASS: standalone EXE, concurrent cold launches, dependency discovery, warm cache reuse'

    $InputFile = Join-Path $TestRoot 'test tone.wav'
    $OutputDirectory = Join-Path $TestRoot 'converted audio'
    & (Join-Path $ProjectRoot 'tools\ffmpeg.exe') -hide_banner -loglevel error -f lavfi -i 'sine=frequency=1000:duration=1' $InputFile
    if ($LASTEXITCODE -ne 0) { throw 'Could not generate test tone' }
    $convert = Start-Process -FilePath $Exe -WorkingDirectory $TestRoot -ArgumentList ('--cli "{0}" "{1}"' -f $InputFile, $OutputDirectory) -PassThru -Wait -RedirectStandardOutput (Join-Path $TestRoot 'conversion.txt') -RedirectStandardError (Join-Path $TestRoot 'conversion-error.txt')
    if ($convert.ExitCode -ne 0) { throw "Standalone conversion failed: $(Get-Content (Join-Path $TestRoot 'conversion-error.txt') -Raw)" }
    $Mp3 = Get-ChildItem -LiteralPath $OutputDirectory -Filter '*.mp3' | Select-Object -First 1
    if (-not $Mp3 -or $Mp3.Length -eq 0) { throw 'Converted MP3 missing' }
    & (Join-Path $ProjectRoot 'tools\ffmpeg.exe') -hide_banner -loglevel error -i $Mp3.FullName -f null -
    if ($LASTEXITCODE -ne 0) { throw 'MP3 decoding failed' }
    Write-Host 'PASS: single EXE converts audio with bundled FFmpeg and handles paths with spaces'

    $Before = @(Get-Process -Name kgg-mp3 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
    $launcher = Start-Process -FilePath $Exe -WorkingDirectory $TestRoot -PassThru
    $window = $null
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        $window = Get-Process -Name kgg-mp3 -ErrorAction SilentlyContinue | Where-Object { $_.Id -ne $launcher.Id -and $_.Id -notin $Before -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1
        if ($window) { break }
    }
    if (-not $window) { throw 'GUI did not open a main window' }
    Write-Host "PASS: double-click launch opens the GUI ($($window.MainWindowTitle))"
    $null = $window.CloseMainWindow()
    Write-Host "Verification files: $TestRoot"
} finally {
    Pop-Location
    $env:LOCALAPPDATA = $OriginalLocal
    $env:PATH = $OriginalPath
    $env:JAVA_HOME = $OriginalJava
}
