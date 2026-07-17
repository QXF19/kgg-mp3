@echo off
setlocal
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-windows.ps1"
if errorlevel 1 (
  echo.
  echo 打包失败，请查看上面的错误信息。
  pause
  exit /b 1
)
echo.
echo 打包成功，文件位于 dist 文件夹。
pause
