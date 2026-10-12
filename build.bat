@echo off
setlocal
chcp 65001 >nul
set "ROOT=%~dp0"
set "JAVAC=javac"
set "JAR=jar"
if exist "%ROOT%..\_jdk17\jdk-17.0.19+10\bin\javac.exe" set "JAVAC=%ROOT%..\_jdk17\jdk-17.0.19+10\bin\javac.exe"
if exist "%ROOT%..\_jdk17\jdk-17.0.19+10\bin\jar.exe" set "JAR=%ROOT%..\_jdk17\jdk-17.0.19+10\bin\jar.exe"
if not exist "%ROOT%build\classes" mkdir "%ROOT%build\classes"
if not exist "%ROOT%tools\flatlaf.jar" (
  echo Please run install-ui.ps1 first.
  exit /b 1
)
"%JAVAC%" -encoding UTF-8 -cp "%ROOT%tools\flatlaf.jar" -d "%ROOT%build\classes" "%ROOT%src\main\java\cn\gkkstudio\GkkMp3App.java" "%ROOT%src\main\java\cn\gkkstudio\AudioItem.java" "%ROOT%src\main\java\cn\gkkstudio\SystemCapabilities.java" "%ROOT%src\main\java\cn\gkkstudio\KgmDecoder.java" "%ROOT%src\main\java\cn\gkkstudio\KggV5Decoder.java" "%ROOT%src\main\java\cn\gkkstudio\MediaEngine.java" "%ROOT%src\main\java\cn\gkkstudio\MainFrame.java"
if errorlevel 1 exit /b 1
pushd "%ROOT%build\classes"
"%JAR%" -xf "%ROOT%tools\flatlaf.jar"
popd
if errorlevel 1 exit /b 1
if exist "%ROOT%src\main\resources" xcopy "%ROOT%src\main\resources\*" "%ROOT%build\classes\" /E /I /Y >nul
if errorlevel 1 exit /b 1
"%JAR%" --create --file "%ROOT%build\GkkAudioStudio.jar" --main-class cn.gkkstudio.GkkMp3App -C "%ROOT%build\classes" .
if errorlevel 1 exit /b 1
echo.
echo 构建成功: build\GkkAudioStudio.jar
