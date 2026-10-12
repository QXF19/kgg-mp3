param([switch]$Force)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ToolsDirectory = Join-Path $ProjectRoot 'tools'
$ArchivePath = Join-Path $ToolsDirectory '.sqlite-download.zip'
$DownloadUrl = 'https://www.sqlite.org/2026/sqlite-tools-win-x64-3530300.zip'
$ExpectedSha256 = 'C90FE36442CF573E8A19B3E8733D121622B8E2D5A0C47CBDA97AEBA9517D1C45'
$Target = Join-Path $ToolsDirectory 'sqlite3.exe'

New-Item -ItemType Directory -Force -Path $ToolsDirectory | Out-Null
if ((Test-Path -LiteralPath $Target) -and -not $Force) {
    Write-Host 'SQLite is already installed. Use -Force to update.' -ForegroundColor Green
    exit 0
}

Write-Host 'Downloading SQLite 3.53.3...'
if (Get-Command 'curl.exe' -ErrorAction SilentlyContinue) {
    & curl.exe -L --fail --retry 5 --retry-delay 2 --output $ArchivePath $DownloadUrl
    if ($LASTEXITCODE -ne 0) { throw "SQLite download failed with exit code $LASTEXITCODE" }
} else {
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ArchivePath -UseBasicParsing
}
$ActualSha256 = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash
if ($ActualSha256 -ne $ExpectedSha256) {
    throw "SQLite archive checksum mismatch. Expected $ExpectedSha256, got $ActualSha256"
}

$ExtractDirectory = Join-Path $env:TEMP ('kgg-studio-sqlite-' + [Guid]::NewGuid().ToString('N'))
try {
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $ExtractDirectory
    $Executable = Get-ChildItem -LiteralPath $ExtractDirectory -Recurse -File -Filter 'sqlite3.exe' | Select-Object -First 1
    if (-not $Executable) { throw 'sqlite3.exe was not found in the official archive.' }
    Copy-Item -LiteralPath $Executable.FullName -Destination $Target -Force
} finally {
    Remove-Item -LiteralPath $ArchivePath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $ExtractDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host 'SQLite installation complete.' -ForegroundColor Green
