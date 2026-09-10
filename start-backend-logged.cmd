@echo off
chcp 65001 >nul
cd /d %~dp0
echo Starting seckill backend, log -> backend.log ...
call mvn spring-boot:run > backend.log 2>&1
echo.
echo Backend stopped. Press any key...
pause >nul