@echo off
setlocal
cd /d "%~dp0"

rem ------------------------------------------------------------------
rem  Git refuses to touch a repository whose owner is a different account:
rem      fatal: detected dubious ownership in repository at '...'
rem  This project may have been created by another account (a sandbox),
rem  so whitelist this exact path once for the current user.
rem ------------------------------------------------------------------
set "REPO_DIR=%CD:\=/%"
git config --global --get-all safe.directory 2>nul | findstr /l /i /c:"%REPO_DIR%" >nul 2>&1
if errorlevel 1 (
    git config --global --add safe.directory "%REPO_DIR%"
    if errorlevel 1 (
        echo.
        echo WARNING: could not add the safe.directory exception automatically.
        echo If the push fails with "dubious ownership", run this by hand:
        echo     git config --global --add safe.directory "%REPO_DIR%"
        echo.
    ) else (
        echo Added git safe.directory exception for %REPO_DIR%
    )
)

rem ===================================================================
rem  Push this project to your GitHub repository.
rem
rem  Usage (create an EMPTY repo on GitHub first, do not add a README):
rem      push-to-github.bat YOUR_GITHUB_USERNAME [REPO_NAME]
rem  Example:
rem      push-to-github.bat zhangsan gomoku
rem
rem  YOUR_GITHUB_USERNAME is the part after github.com/ in your repo URL.
rem  This script is kept ASCII-only on purpose: batch files with non-ASCII
rem  text get mangled by cmd.exe on non-UTF8 code pages.
rem ===================================================================

if "%~1"=="" (
    echo.
    echo Usage  : push-to-github.bat YOUR_GITHUB_USERNAME [REPO_NAME]
    echo Example: push-to-github.bat zhangsan gomoku
    echo.
    echo The username is the part after github.com/ in your repo URL.
    echo.
    exit /b 1
)

set "GH_USER=%~1"
set "GH_REPO=%~2"
if "%GH_REPO%"=="" set "GH_REPO=gomoku"

rem Clash mixed proxy port. Change this line if your port is different.
set "PROXY_PORT=7897"

rem Force device-code login so we do not depend on a browser popup.
set "GCM_GITHUB_AUTHMODES=device"

rem Git Credential Manager is a .NET app with its own HTTP stack,
rem so it needs the proxy too, otherwise it cannot reach github.com.
set "HTTP_PROXY=http://127.0.0.1:%PROXY_PORT%"
set "HTTPS_PROXY=http://127.0.0.1:%PROXY_PORT%"

echo.
echo [1/3] Configuring proxy and login mode ...
git config --local http.proxy  http://127.0.0.1:%PROXY_PORT%
git config --local https.proxy http://127.0.0.1:%PROXY_PORT%
rem Use git's bundled OpenSSL instead of Windows schannel.
rem schannel often fails behind a proxy with:
rem   "schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS"
git config --local http.sslBackend openssl
git config --local credential.credentialStore dpapi
git config --local credential.gitHubAuthModes device
echo      proxy port %PROXY_PORT%, login mode: device code

echo.
echo [2/3] Setting remote origin ...
git remote remove origin >nul 2>&1
git remote add origin https://github.com/%GH_USER%/%GH_REPO%.git
git remote -v

echo.
echo [3/3] Pushing to GitHub ...
echo.
echo      If it asks you to sign in, open https://github.com/login/device
echo      and type in the code printed below.
echo.
git push -u origin main

if errorlevel 1 (
    echo.
    echo ==========================================================
    echo  PUSH FAILED
    echo  Copy the git error above and send it to me.
    echo ==========================================================
    exit /b 1
)

echo.
echo Done. Repository: https://github.com/%GH_USER%/%GH_REPO%
echo Next step: open https://dashboard.render.com and deploy it.
echo.
endlocal
