@echo off
REM Fast deployment script for Windows
REM Usage: deploy.bat [backend|frontend|all]

setlocal enabledelayedexpansion

set SERVER=root@62.171.153.133
set REMOTE_DIR=/root/buildquote

if "%1"=="" set TARGET=all
if not "%1"=="" set TARGET=%1

if "%TARGET%"=="backend" goto :backend
if "%TARGET%"=="be" goto :backend
if "%TARGET%"=="b" goto :backend
if "%TARGET%"=="frontend" goto :frontend
if "%TARGET%"=="fe" goto :frontend
if "%TARGET%"=="f" goto :frontend
if "%TARGET%"=="all" goto :all

echo Usage: deploy.bat [backend^|frontend^|all]
exit /b 1

:backend
echo === Deploying Backend ===
cd backend

echo Building JAR with Gradle...
call gradlew.bat build -x test --no-daemon -q
if errorlevel 1 (
    echo Build failed!
    cd ..
    exit /b 1
)

for %%f in (build\libs\*.jar) do (
    echo %%f | findstr /v "plain" >nul && set JAR_FILE=%%f
)

echo Built: %JAR_FILE%

echo Uploading JAR to server...
scp -o StrictHostKeyChecking=no "%JAR_FILE%" %SERVER%:/tmp/app.jar

echo Restarting backend service...
ssh -o StrictHostKeyChecking=no %SERVER% "docker cp /tmp/app.jar bq-backend:/app/app.jar && docker restart bq-backend && sleep 5 && docker exec bq-postgres psql -U postgres -c \"ALTER USER postgres WITH PASSWORD 'postgres';\" 2>/dev/null; for i in 1 2 3 4 5 6 7 8 9 10; do curl -s http://localhost:8081/api/projects/health >/dev/null 2>&1 && echo Backend is up! && break || sleep 2; done"

cd ..
echo Backend deployed!
if "%TARGET%"=="backend" goto :done
if "%TARGET%"=="be" goto :done
if "%TARGET%"=="b" goto :done

:frontend
echo === Deploying Frontend ===
cd frontend

echo Building Angular app...
call npm run build -- --configuration production
if errorlevel 1 (
    echo Build failed!
    cd ..
    exit /b 1
)

set DIST_DIR=dist\buildquote-frontend\browser
if not exist "%DIST_DIR%" set DIST_DIR=dist\buildquote-frontend

echo Syncing to server...
scp -r -o StrictHostKeyChecking=no "%DIST_DIR%\*" %SERVER%:/tmp/frontend-dist/

ssh -o StrictHostKeyChecking=no %SERVER% "docker cp /tmp/frontend-dist/. bq-frontend:/usr/share/nginx/html/ && docker exec bq-frontend nginx -s reload"

cd ..
echo Frontend deployed!
goto :done

:all
goto :backend

:done
echo === Deployment complete! ===
