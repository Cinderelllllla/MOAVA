@echo off
chcp 65001 > nul
setlocal

set "ROOT=%~dp0"
set "COMPOSE_FILE=%ROOT%infra\docker\docker-compose.yml"

echo [1/4] 실행 환경을 점검합니다...

docker info > nul 2>&1
if errorlevel 1 (
    echo [오류] Docker Desktop이 실행 중이지 않습니다. Docker Desktop을 먼저 실행해주세요.
    pause
    exit /b 1
)

where npm.cmd > nul 2>&1
if errorlevel 1 (
    echo [오류] Node.js / npm을 찾을 수 없습니다. Node.js를 설치하고 PATH를 확인해주세요.
    pause
    exit /b 1
)

if not exist "%ROOT%ml\.env" (
    echo [오류] ml\.env 파일이 없습니다.
    echo        ml\.env.example을 복사해 ml\.env를 만들고 MOAVA_INTERNAL_KEY를 설정해주세요.
    echo        값은 backend\src\main\resources\application-secret.properties 의 moava.ml.internal-key 와 같아야 합니다.
    pause
    exit /b 1
)

if not exist "%ROOT%backend\src\main\resources\application-secret.properties" (
    echo [경고] backend\src\main\resources\application-secret.properties 파일이 없습니다.
    echo        DB 접속 및 ML 인증이 실패할 수 있습니다.
)

set "NEED_NPM_INSTALL=0"
if not exist "%ROOT%frontend\node_modules\.package-lock.json" goto :need_npm_install
powershell -NoProfile -Command "$nm=(Get-Item '%ROOT%frontend\node_modules\.package-lock.json').LastWriteTimeUtc; $pj=(Get-Item '%ROOT%frontend\package.json').LastWriteTimeUtc; $pl=if(Test-Path '%ROOT%frontend\package-lock.json'){(Get-Item '%ROOT%frontend\package-lock.json').LastWriteTimeUtc}else{[datetime]::MinValue}; if($pj -gt $nm -or $pl -gt $nm){exit 1}else{exit 0}" > nul 2>&1
if errorlevel 1 goto :need_npm_install
goto :skip_npm_install

:need_npm_install
echo [안내] frontend 의존성을 설치/갱신합니다...
pushd "%ROOT%frontend"
call npm.cmd install
if errorlevel 1 (
    popd
    echo [오류] frontend npm install에 실패했습니다.
    pause
    exit /b 1
)
popd

:skip_npm_install
echo.
echo [2/4] Docker 컨테이너 [Elasticsearch :9200, ML :8000] 를 시작하고 준비 완료까지 대기합니다...
docker compose -f "%COMPOSE_FILE%" up -d --build --wait
if errorlevel 1 (
    echo [오류] Docker 컨테이너 기동에 실패했습니다. 위 로그를 확인해주세요.
    pause
    exit /b 1
)

echo.
echo [3/4] 백엔드 [:8080] 와 프론트엔드 [:3000] 를 확인 및 실행합니다...

netstat -ano | findstr /R /C:":8080 .*LISTENING" > nul 2>&1
if not errorlevel 1 goto :backend_running
start "MOAVA-Backend" /D "%ROOT%backend" cmd /k "chcp 65001 > nul && call mvnw.cmd spring-boot:run"
goto :check_frontend

:backend_running
echo [안내] 백엔드 [:8080] 는 이미 실행 중입니다.

:check_frontend
netstat -ano | findstr /R /C:":3000 .*LISTENING" > nul 2>&1
if not errorlevel 1 goto :frontend_running
start "MOAVA-Frontend" /D "%ROOT%frontend" cmd /k "chcp 65001 > nul && call npm.cmd run dev"
goto :wait_frontend

:frontend_running
echo [안내] 프론트엔드 [:3000] 는 이미 실행 중입니다.

:wait_frontend
echo.
echo [4/4] 프론트엔드 기동 상태를 확인합니다...
powershell -NoProfile -Command "$ok=$false; for($i=0;$i -lt 15;$i++){ try { $r=Invoke-WebRequest -UseBasicParsing http://localhost:3000 -TimeoutSec 2; if($r.StatusCode -eq 200){ $ok=$true; break } } catch {}; Start-Sleep -Seconds 1 }; if($ok){ exit 0 } else { exit 1 }" > nul 2>&1
if errorlevel 1 (
    echo [경고] Frontend [:3000] 응답이 아직 확인되지 않았습니다. MOAVA-Frontend 창 로그를 확인해주세요.
) else (
    echo [확인] Frontend가 http://localhost:3000 에서 정상 기동되었습니다.
)

echo.
echo   - Frontend      : http://localhost:3000
echo   - Backend       : http://localhost:8080
echo   - ML Health     : http://localhost:8000/health
echo   - Elasticsearch : http://localhost:9200
echo.
echo 종료 시 stop-dev.bat 를 실행하면 Docker 컨테이너를 정리할 수 있습니다.
endlocal