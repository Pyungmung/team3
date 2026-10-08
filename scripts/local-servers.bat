@echo off
chcp 65001 >nul
rem [담당: 송귀성] 더블클릭용: local-servers.ps1 을 실행한다 (인자 없으면 서버 켜기). 사용법은 local-servers.ps1 맨 위 설명 참고.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0local-servers.ps1" %*
if "%~1"=="" pause
