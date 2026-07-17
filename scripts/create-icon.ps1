$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Assets = Join-Path $ProjectRoot 'assets'
$Png = Join-Path $Assets 'app.png'
$Ico = Join-Path $Assets 'app.ico'
$Ffmpeg = Join-Path $ProjectRoot 'tools\ffmpeg.exe'
New-Item -ItemType Directory -Force -Path $Assets | Out-Null

Add-Type -AssemblyName System.Drawing
$Bitmap = [System.Drawing.Bitmap]::new(512, 512)
$Graphics = [System.Drawing.Graphics]::FromImage($Bitmap)
$Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$Graphics.Clear([System.Drawing.Color]::Transparent)

$Background = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.Rectangle]::new(0, 0, 512, 512),
    [System.Drawing.Color]::FromArgb(108, 85, 245),
    [System.Drawing.Color]::FromArgb(74, 50, 205),
    45
)
$Graphics.FillEllipse($Background, 16, 16, 480, 480)

$White = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$SoftWhite = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(205, 255, 255, 255))
$Graphics.FillRectangle($SoftWhite, 132, 150, 34, 190)
$Graphics.FillRectangle($SoftWhite, 204, 112, 34, 266)
$Graphics.FillRectangle($White, 276, 178, 34, 154)
$Graphics.FillRectangle($White, 348, 132, 34, 246)
$Graphics.FillEllipse($White, 316, 330, 105, 78)
$Graphics.FillEllipse($SoftWhite, 168, 344, 105, 78)

$Graphics.Dispose()
$Background.Dispose()
$White.Dispose()
$SoftWhite.Dispose()
$Bitmap.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png)
$Bitmap.Dispose()

if (-not (Test-Path -LiteralPath $Ffmpeg)) { throw 'Run install-ffmpeg.bat first.' }
& $Ffmpeg -hide_banner -loglevel error -y -i $Png -vf 'scale=256:256' -frames:v 1 -f ico $Ico
if ($LASTEXITCODE -ne 0) { throw "Icon conversion failed with exit code $LASTEXITCODE" }
Write-Host "Created $Ico" -ForegroundColor Green
