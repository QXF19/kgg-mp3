param(
    [string]$Version = '1.1.2',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDirectory = Join-Path $ProjectRoot 'build'
$InputDirectory = Join-Path $BuildDirectory 'package-input'
$DistDirectory = Join-Path $ProjectRoot 'dist'
$AppImage = Join-Path $DistDirectory 'kgg-mp3'
$Archive = Join-Path $DistDirectory 'kgg-mp3-windows-x64.zip'
$Jar = Join-Path $BuildDirectory 'GkkAudioStudio.jar'
$Ffmpeg = Join-Path $ProjectRoot 'tools\ffmpeg.exe'
$Ffprobe = Join-Path $ProjectRoot 'tools\ffprobe.exe'
$Sqlite = Join-Path $ProjectRoot 'tools\sqlite3.exe'
$Icon = Join-Path $ProjectRoot 'assets\app.ico'

if (-not $SkipBuild) {
    & (Join-Path $ProjectRoot 'build.bat')
    if ($LASTEXITCODE -ne 0) { throw "Java build failed with exit code $LASTEXITCODE" }
}
if (-not (Test-Path -LiteralPath $Jar)) { throw 'Application JAR is missing. Run build.bat first.' }
if (-not (Test-Path -LiteralPath $Ffmpeg) -or -not (Test-Path -LiteralPath $Ffprobe)) {
    throw 'FFmpeg is missing. Run install-ffmpeg.bat first.'
}
if (-not (Test-Path -LiteralPath $Sqlite)) {
    throw 'SQLite is missing. Run install-sqlite.bat first.'
}
if (-not (Test-Path -LiteralPath $Icon)) { throw 'assets\app.ico is missing.' }

$Jpackage = $null
if ($env:JAVA_HOME) {
    $Candidate = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
    if (Test-Path -LiteralPath $Candidate) { $Jpackage = $Candidate }
}
if (-not $Jpackage) {
    $LocalJpackage = Join-Path $ProjectRoot '..\_jdk17\jdk-17.0.19+10\bin\jpackage.exe'
    if (Test-Path -LiteralPath $LocalJpackage) { $Jpackage = (Resolve-Path $LocalJpackage).Path }
}
if (-not $Jpackage) {
    $Command = Get-Command 'jpackage.exe' -ErrorAction SilentlyContinue
    if ($Command) { $Jpackage = $Command.Source }
}
if (-not $Jpackage) { throw 'JDK 17+ jpackage was not found.' }

foreach ($CleanupTarget in @($InputDirectory, $AppImage, $Archive)) {
    $ResolvedTarget = [IO.Path]::GetFullPath($CleanupTarget)
    $AllowedRoot = [IO.Path]::GetFullPath($ProjectRoot) + [IO.Path]::DirectorySeparatorChar
    if (-not $ResolvedTarget.StartsWith($AllowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Packaging cleanup target is outside the project: $ResolvedTarget"
    }
}
if (Test-Path -LiteralPath $InputDirectory) { Remove-Item -LiteralPath $InputDirectory -Recurse -Force }
if (Test-Path -LiteralPath $AppImage) { Remove-Item -LiteralPath $AppImage -Recurse -Force }
if (Test-Path -LiteralPath $Archive) { Remove-Item -LiteralPath $Archive -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $InputDirectory 'tools'), $DistDirectory | Out-Null

Copy-Item -LiteralPath $Jar -Destination (Join-Path $InputDirectory 'GkkAudioStudio.jar')
Copy-Item -LiteralPath $Ffmpeg -Destination (Join-Path $InputDirectory 'tools\ffmpeg.exe')
Copy-Item -LiteralPath $Ffprobe -Destination (Join-Path $InputDirectory 'tools\ffprobe.exe')
Copy-Item -LiteralPath $Sqlite -Destination (Join-Path $InputDirectory 'tools\sqlite3.exe')

& $Jpackage `
    --type app-image `
    --name 'kgg-mp3' `
    --dest $DistDirectory `
    --input $InputDirectory `
    --main-jar 'GkkAudioStudio.jar' `
    --main-class 'cn.gkkstudio.GkkMp3App' `
    --add-launcher "kgg-mp3-cli=$(Join-Path $ProjectRoot 'launcher-cli.properties')" `
    --icon $Icon `
    --vendor 'QXF19' `
    --app-version $Version `
    --description 'KGG/KGM high-quality MP3 converter'
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

& (Join-Path $ProjectRoot 'scripts\set-utf8-manifest.ps1') -Executables @(
    (Join-Path $AppImage 'kgg-mp3.exe'),
    (Join-Path $AppImage 'kgg-mp3-cli.exe')
)

Copy-Item -LiteralPath (Join-Path $ProjectRoot 'README.md') -Destination $AppImage
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'LICENSE') -Destination $AppImage
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'LICENSE-FlatLaf.txt') -Destination $AppImage
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'THIRD_PARTY_NOTICES.md') -Destination $AppImage
Compress-Archive -LiteralPath $AppImage -DestinationPath $Archive -CompressionLevel Optimal

Write-Host "Portable EXE package created: $Archive" -ForegroundColor Green
