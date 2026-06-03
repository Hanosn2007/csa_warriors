@echo off
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\javac.exe" (
            set "JAVAC=%JAVA_HOME%\bin\javac.exe"
            set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
            goto compile
        )
    )
    echo JDK 17 or newer is required.
    echo Please install JDK and make sure javac is available in PATH.
    pause
    exit /b 1
)
set "JAVAC=javac"
set "JAVAW=javaw"

if not exist out mkdir out

:compile
echo Compiling Java Warrior Arena...
"%JAVAC%" --release 17 -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
    echo.
    echo Compile failed. Fix the Java errors above, then double-click this file again.
    pause
    exit /b 1
)

echo Starting visual simulation UI...
start "" "%JAVAW%" -cp out ArenaUiMain
exit /b 0
