@echo off
setlocal
chcp 65001 >nul
set "ROOT=%~dp0"
set "JAVA=javaw"
if exist "%ROOT%..\_jdk17\jdk-17.0.19+10\bin\javaw.exe" set "JAVA=%ROOT%..\_jdk17\jdk-17.0.19+10\bin\javaw.exe"
if not exist "%ROOT%build\GkkAudioStudio.jar" call "%ROOT%build.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
start "GKK Audio Studio" "%JAVA%" -jar "%ROOT%build\GkkAudioStudio.jar"
