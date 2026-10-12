@echo off
setlocal
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-sqlite.ps1"
if errorlevel 1 (
  echo.
  echo SQLite 安装失败，请查看上面的错误信息。
  pause
  exit /b 1
)
echo SQLite 安装完成。
