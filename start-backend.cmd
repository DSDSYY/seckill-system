@echo off
chcp 65001 >nul
cd /d %~dp0
echo ============================================
echo  秒杀后端启动脚本 (port 8081)
echo  前置依赖：JDK 17+ / Maven 3.9+ / MySQL 8 / Redis / RabbitMQ
echo  如果下面报 "port 8081 already in use"
echo  说明后端已在运行, 请勿重复启动。
echo ============================================
call mvn spring-boot:run
echo.
echo 后端已停止。按任意键关闭窗口...
pause >nul