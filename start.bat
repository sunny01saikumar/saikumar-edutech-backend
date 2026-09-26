@echo off
cd /d "%~dp0"
echo =========================================================
echo                STARTING NEXUS AI ENGINE
echo =========================================================

echo Starting Spring Boot Backend (Port 8080)...
start "NEXUS AI Backend" cmd /k "cd /d "%~dp0backend" && mvnw.cmd spring-boot:run"

echo Starting React Frontend (Port 3000)...
start "NEXUS AI Frontend" cmd /k "cd /d "%~dp0frontend" && npm run dev"

echo =========================================================
echo NEXUS AI is starting up!
echo Frontend: http://localhost:3000
echo Backend:  http://localhost:8080
echo =========================================================
