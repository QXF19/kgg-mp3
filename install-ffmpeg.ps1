param([switch]$Force)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ToolsDirectory = Join-Path $ProjectRoot 'tools'
$ArchivePath = Join-Path $ToolsDirectory '.ffmpeg-download.zip'
$DownloadUrl = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip'
New-Item -ItemType Directory -Force -Path $ToolsDirectory | Out-Null
if ((Test-Path (Join-Path $ToolsDirectory 'ffmpeg.exe')) -and -not $Force) {
    Write-Host 'FFmpeg is already installed. Use -Force to update.' -ForegroundColor Green
    exit 0
}
Write-Host 'Downloading FFmpeg Essentials...'
if (Get-Command 'curl.exe' -ErrorAction SilentlyContinue) {
    & curl.exe -L --fail --retry 5 --retry-delay 2 --continue-at - --output $ArchivePath $DownloadUrl
    if ($LASTEXITCODE -ne 0) { throw "FFmpeg download failed with exit code $LASTEXITCODE" }
} else {
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ArchivePath -UseBasicParsing
}
$ExtractDirectory = Join-Path $env:TEMP ('gkk-studio-ffmpeg-' + [Guid]::NewGuid().ToString('N'))
Expand-Archive -LiteralPath $ArchivePath -DestinationPath $ExtractDirectory
$Bin = Get-ChildItem -LiteralPath $ExtractDirectory -Directory | Select-Object -First 1 | ForEach-Object { Join-Path $_.FullName 'bin' }
Copy-Item -LiteralPath (Join-Path $Bin 'ffmpeg.exe') -Destination $ToolsDirectory -Force
Copy-Item -LiteralPath (Join-Path $Bin 'ffprobe.exe') -Destination $ToolsDirectory -Force
Remove-Item -LiteralPath $ArchivePath -Force -ErrorAction SilentlyContinue
Write-Host 'Installation complete. Double-click run.bat to start.' -ForegroundColor Green
