@echo off
cd /d "%~dp0"
if not exist "node_modules" (
  echo [Pleos Stage Server] Installing dependencies...
  call npm install
)
echo [Pleos Stage Server] Starting Signaling Server + HTTPS Tunnel...
node tunnel.js
