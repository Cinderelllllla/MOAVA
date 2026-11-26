@echo off
setlocal
chcp 65001 > nul

set "ROOT=%~dp0"
set "COMPOSE_FILE=%ROOT%infra\docker\docker-compose.yml"

echo Docker 컨테이너 [Elasticsearch, ML] 를 종료합니다...
docker compose -f "%COMPOSE_FILE%" down
if errorlevel 1 (
    echo [오류] Docker 컨테이너 종료 중 문제가 발생했습니다.
    pause
    exit /b 1
)

echo 완료되었습니다. 백엔드/프론트엔드 실행 창은 각각 Ctrl+C 또는 창 닫기로 종료해주세요.
endlocal
