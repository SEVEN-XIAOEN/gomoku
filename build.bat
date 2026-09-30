@echo off
setlocal
cd /d "%~dp0"

echo [1/3] Cleaning previous build ...
if exist out rmdir /s /q out
mkdir out

rem Fail fast if a running server still holds the old jar open.
rem Windows locks the jar file, so packaging would fail halfway through.
if exist gomoku-server.jar (
    ren gomoku-server.jar gomoku-server.jar.lockcheck >nul 2>&1
    if errorlevel 1 (
        echo.
        echo ==========================================================
        echo  gomoku-server.jar is in use by a running server.
        echo  Stop the server first, then run build.bat again.
        echo  Stop with:  taskkill /IM java.exe /F
        echo ==========================================================
        exit /b 1
    )
    ren gomoku-server.jar.lockcheck gomoku-server.jar >nul 2>&1
)

echo [2/3] Compiling Java sources ...
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
    echo.
    echo Build failed. Check the errors above.
    exit /b 1
)

echo [3/3] Packaging runnable jar ...
jar --create --file gomoku-server.jar --main-class GomokuServer -C out .
if errorlevel 1 (
    echo.
    echo Packaging failed.
    exit /b 1
)

echo.
echo Build OK : gomoku-server.jar
echo Run it   : run.bat    or    java -jar gomoku-server.jar
endlocal
