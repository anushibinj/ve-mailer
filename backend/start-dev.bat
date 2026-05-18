@echo off
cd /d "%~dp0"
echo Starting Spring Boot Server (dev profile)...
mvn spring-boot:run -Dspring-boot.run.profiles=dev
