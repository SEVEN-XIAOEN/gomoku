@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo [1/3] 清理旧的编译结果...
if exist out rmdir /s /q out
mkdir out

echo [2/3] 编译 Java 源码...
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
    echo.
    echo 编译失败，请检查上面的错误信息。
    exit /b 1
)

echo [3/3] 打包成可执行 jar...
jar --create --file gomoku-server.jar --main-class GomokuServer -C out .
if errorlevel 1 (
    echo.
    echo 打包失败。
    exit /b 1
)

echo.
echo 构建完成：gomoku-server.jar
echo 运行方式：run.bat   或者   java -jar gomoku-server.jar
endlocal
