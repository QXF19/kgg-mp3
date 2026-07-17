@echo off
setlocal
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-ffmpeg.ps1"
if errorlevel 1 (
  echo.
  echo 安装失败，请检查网络连接后重试。
  pause
  exit /b 1
)
pause
