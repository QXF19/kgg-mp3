param()
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Archive = Join-Path $ProjectRoot 'dist\kgg-mp3-windows-x64.zip'
$Output = Join-Path $ProjectRoot 'dist\kgg-mp3.exe'
$Compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $Archive)) { throw 'Build the portable ZIP first.' }
if (-not (Test-Path -LiteralPath $Compiler)) { throw '.NET Framework 4 compiler not found.' }
$HashFile = Join-Path $ProjectRoot 'build\payload.sha256'
[IO.File]::WriteAllText($HashFile, (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash)
& $Compiler /nologo /target:winexe /platform:x64 /optimize+ "/out:$Output" `
    "/win32icon:$(Join-Path $ProjectRoot 'assets\app.ico')" `
    "/win32manifest:$(Join-Path $PSScriptRoot 'single-exe.manifest')" `
    /reference:System.Windows.Forms.dll /reference:System.Drawing.dll `
    /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll `
    "/resource:$Archive,payload.zip" "/resource:$HashFile,payload.sha256" `
    (Join-Path $PSScriptRoot 'SingleFileLauncher.cs')
if ($LASTEXITCODE -ne 0) { throw "Single EXE compilation failed: $LASTEXITCODE" }
Write-Host "Single-file EXE created: $Output" -ForegroundColor Green
