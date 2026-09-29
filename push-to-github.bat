@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ===================================================================
rem 把本项目推送到你的 GitHub 仓库。
rem 用法（先到 GitHub 网站上建一个空仓库，不要勾选 README）：
rem     push-to-github.bat 你的GitHub用户名 仓库名
rem 例如：
rem     push-to-github.bat zhangsan gomoku
rem ===================================================================

if "%~1"=="" (
    echo 用法：push-to-github.bat 你的GitHub用户名 仓库名
    echo 例如：push-to-github.bat zhangsan gomoku
    exit /b 1
)

set "GH_USER=%~1"
set "GH_REPO=%~2"
if "%GH_REPO%"=="" set "GH_REPO=gomoku"

rem 本机的 Clash 混合代理端口。如果以后改了，改这一行即可。
set "PROXY_PORT=7897"

echo [1/4] 临时给 git 配置代理 127.0.0.1:%PROXY_PORT%（只对本项目生效，不影响全局设置）...
git config --local http.proxy  http://127.0.0.1:%PROXY_PORT%
git config --local https.proxy http://127.0.0.1:%PROXY_PORT%

echo [2/4] 关联远程仓库 github.com/%GH_USER%/%GH_REPO%.git ...
git remote remove origin >nul 2>&1
git remote add origin https://github.com/%GH_USER%/%GH_REPO%.git

echo [3/4] 测试连通性...
git ls-remote --heads origin >nul 2>&1
if errorlevel 1 (
    echo.
    echo 连不上远程仓库。常见原因：
    echo   1. 仓库还没在 GitHub 上创建
    echo   2. 用户名或仓库名写错了
    echo   3. Clash 没开，或者混合端口不是 %PROXY_PORT%
    exit /b 1
)

echo [4/4] 推送代码（首次会弹出浏览器，请登录 GitHub 账号）...
git push -u origin main
if errorlevel 1 (
    echo.
    echo 推送失败，请把上面的报错发给我。
    exit /b 1
)

echo.
echo 推送成功！仓库地址： https://github.com/%GH_USER%/%GH_REPO%
echo 接下来去 https://render.com 连接这个仓库，即可自动部署。
endlocal
