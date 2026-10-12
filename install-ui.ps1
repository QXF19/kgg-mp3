$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Target = Join-Path $ProjectRoot 'tools\flatlaf.jar'
$Expected = '687BE33E1E3EFACE1681FD194732D16E008126A47B6116B4301E7070C918C4C9'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Target) | Out-Null
if (-not (Test-Path -LiteralPath $Target)) {
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/formdev/flatlaf/3.6.1/flatlaf-3.6.1.jar' -OutFile $Target -UseBasicParsing
}
if ((Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash -ne $Expected) {
    throw 'FlatLaf checksum mismatch.'
}
Write-Host 'FlatLaf 3.6.1 ready.'
