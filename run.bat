@echo off
setlocal
cd /d "%~dp0"

rem Build automatically the first time
if not exist gomoku-server.jar (
    echo Jar not found, running build.bat first ...
    call build.bat
    if errorlevel 1 exit /b 1
)

echo.
echo Open your browser at : http://localhost:8080
echo Press Ctrl + C to stop the server.
echo.

rem Extra arguments are passed through, e.g.  run.bat 9000
java -jar gomoku-server.jar %*

endlocal
