@echo off
title Claw Assistant - Docker Environment Installer
echo ============================================================
echo   Claw Assistant - Docker One-Click Installer
echo   Requires ADMIN privileges. Click "Yes" on UAC prompt.
echo ============================================================
echo.

echo [1/4] Enabling WSL feature (Microsoft-Windows-Subsystem-Linux) ...
dism /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
echo.

echo [2/4] Enabling Virtual Machine Platform ...
dism /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
echo.

echo [3/4] Installing WSL2 kernel and setting default version 2 ...
wsl --update
wsl --set-default-version 2
echo.

echo [4/4] Downloading and installing Docker Desktop (~500MB, may take 5-15 min) ...
winget install --id Docker.DockerDesktop -e --accept-source-agreements --accept-package-agreements

echo.
echo ============================================================
echo   INSTALL FINISHED!
echo.
echo   NEXT STEPS:
echo     1. RESTART your computer (required after enabling WSL)
echo     2. After reboot, open Docker Desktop and wait until the
echo        whale icon in the system tray is steady / green
echo     3. Then run in the project directory: docker compose up -d
echo ============================================================
echo.
pause
