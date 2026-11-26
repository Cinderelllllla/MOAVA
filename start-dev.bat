@echo off
setlocal
chcp 65001 > nul

set "ROOT=%~dp0"
set "COMPOSE_FILE=%ROOT%infra\docker\docker-compose.yml"

echo [1/4] 실행 환경을 점검합니다...

docker info > nul 2>&1
if errorlevel 1 (
    echo [오류] Docker Desktop이 실행 중이지 않습니다. Docker Desktop을 먼저 실행해주세요.
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

if not exist "%ROOT%frontend\node_modules" (
    echo [안내] frontend\node_modules가 없어 npm install을 먼저 실행합니다...
    pushd "%ROOT%frontend"
    call npm install
    if errorlevel 1 (
        popd
        echo [오류] frontend npm install에 실패했습니다.
        pause
        exit /b 1
    )
    popd
)

echo.
echo [2/4] Docker 컨테이너 [Elasticsearch :9200, ML :8000] 를 시작하고 준비 완료까지 대기합니다...
docker compose -f "%COMPOSE_FILE%" up -d --build --wait
if errorlevel 1 (
    echo [오류] Docker 컨테이너 기동에 실패했습니다. 위 로그를 확인해주세요.
    pause
    exit /b 1
)

echo.
echo [3/4] 백엔드 [:8080] 와 프론트엔드 [:3000] 를 새 창으로 실행합니다...
start "MOAVA-Backend" /D "%ROOT%backend" cmd /k "mvnw.cmd spring-boot:run"
start "MOAVA-Frontend" /D "%ROOT%frontend" cmd /k "npm run dev"

echo.
echo [4/4] 실행 요청이 완료되었습니다.
echo   - Frontend      : http://localhost:3000
echo   - Backend       : http://localhost:8080
echo   - ML Health     : http://localhost:8000/health
echo   - Elasticsearch : http://localhost:9200
echo.
echo 종료 시 stop-dev.bat 를 실행하면 Docker 컨테이너를 정리할 수 있습니다.
endlocal