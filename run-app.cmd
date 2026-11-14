@echo off
rem Starts the application with the built-in H2 database (no database installation needed).
rem Then open http://localhost:8080/books  and  http://localhost:8080/swagger-ui.html
rem Press Ctrl+C in this window to stop the application.
cd /d "%~dp0"
echo Starting the application... when you see "Started LibraryApiApplication", open:
echo    http://localhost:8080/books
echo    http://localhost:8080/swagger-ui.html
echo.
call mvnw.cmd -B spring-boot:run -Dspring-boot.run.profiles=h2
pause
