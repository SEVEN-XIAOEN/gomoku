@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem 没有 jar 就先自动构建一次
if not exist gomoku-server.jar (
    echo 还没有构建过，先执行 build.bat ...
    call build.bat
    if errorlevel 1 exit /b 1
)

echo.
echo 服务启动后，请在浏览器打开： http://localhost:8080
echo 按 Ctrl + C 停止服务
echo.

rem 后面的参数会传给程序，例如 run.bat 9000 表示改用 9000 端口
java -jar gomoku-server.jar %*

endlocal
