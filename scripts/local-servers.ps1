# [담당: 송귀성] 로컬 서버 3개(프론트 3000 / AI 엔진 8000 / 백엔드 8080)를 켜고 끄는 스크립트
#
# Claude 앱의 미리보기 서버(preview_start)는 앱이 한꺼번에 정지시킬 때가 있어서, 앱과 별개의 독립 프로세스로 띄운다.
# 사용법 (프로젝트 폴더에서):
#   .\scripts\local-servers.ps1            켜기 (이미 켜진 서버는 건너뜀)
#   .\scripts\local-servers.ps1 stop       끄기
#   .\scripts\local-servers.ps1 status     상태 보기
#   .\scripts\local-servers.ps1 restart    껐다가 켜기
# 더블클릭으로 쓰려면 같은 폴더의 local-servers.bat 을 쓴다. 로그는 logs\ 폴더에 쌓인다 (*.log는 Git에 안 올라간다).
# 한계: PC가 절전에 들어가거나 재부팅하면 서버도 같이 꺼진다 (다시 켜기만 하면 된다).
param(
    [ValidateSet('start', 'stop', 'status', 'restart')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$LogDir = Join-Path $Root 'logs'
$PidFile = Join-Path $LogDir 'local-servers.pids.json'

# 켜는 순서대로 (AI 엔진 -> 백엔드 -> 프론트). ReadyPath: 응답이 오면 준비된 것으로 본다 (400 같은 오류 응답도 "켜져 있음"이다).
$Servers = @(
    @{ Key = 'ai';       Name = 'AI 엔진';    Port = 8000; ReadyPath = '/health';                                      WaitSec = 90  },
    @{ Key = 'backend';  Name = '백엔드';     Port = 8080; ReadyPath = '/api/auth/check-email?email=ready@example.com'; WaitSec = 240 },
    @{ Key = 'frontend'; Name = '프론트엔드'; Port = 3000; ReadyPath = '/';                                            WaitSec = 30  }
)

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Test-Ready([hashtable]$Server) {
    try {
        Invoke-WebRequest -Uri ("http://localhost:{0}{1}" -f $Server.Port, $Server.ReadyPath) -UseBasicParsing -TimeoutSec 5 | Out-Null
        return $true
    } catch {
        # 서버가 400/404 등으로 답한 경우도 켜져 있는 것이다. 연결 자체가 안 되면 Response가 없다.
        return [bool]$_.Exception.Response
    }
}

function Start-Server([hashtable]$Server) {
    if (Test-Port $Server.Port) {
        Write-Host ("[건너뜀] {0} (포트 {1})는 이미 켜져 있어요" -f $Server.Name, $Server.Port)
        return $null
    }
    New-Item -ItemType Directory -Force $LogDir | Out-Null
    $out = Join-Path $LogDir ("{0}.out.log" -f $Server.Key)
    $err = Join-Path $LogDir ("{0}.err.log" -f $Server.Key)

    switch ($Server.Key) {
        'ai' {
            $file = Join-Path $Root 'customhouse-ai\.venv\Scripts\python.exe'
            $args = @('-m', 'uvicorn', 'main:app', '--reload', '--port', '8000', '--app-dir', 'customhouse-ai')
        }
        'backend' {
            $file = Join-Path $Root 'backend\mvnw.cmd'
            $args = @('-f', 'backend\pom.xml', 'spring-boot:run', '-Dspring-boot.run.profiles=local-mysql')
        }
        'frontend' {
            $file = (Get-Command python -ErrorAction Stop).Source
            $args = @('frontend/serve.py')
        }
    }
    $p = Start-Process -FilePath $file -ArgumentList $args -WorkingDirectory $Root -WindowStyle Hidden `
        -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    Write-Host ("[시작] {0} (포트 {1}) 켜는 중... pid {2}" -f $Server.Name, $Server.Port, $p.Id)
    return $p.Id
}

function Wait-Server([hashtable]$Server) {
    $deadline = (Get-Date).AddSeconds($Server.WaitSec)
    while ((Get-Date) -lt $deadline) {
        if ((Test-Port $Server.Port) -and (Test-Ready $Server)) {
            Write-Host ("[완료] {0} 준비됨  http://localhost:{1}" -f $Server.Name, $Server.Port)
            return $true
        }
        Start-Sleep -Seconds 3
    }
    Write-Host ("[주의] {0}가 {1}초 안에 준비되지 않았어요. logs\{2}.err.log 를 확인하세요" -f $Server.Name, $Server.WaitSec, $Server.Key)
    return $false
}

function Stop-All {
    # 1) 켤 때 기록해 둔 시작 프로세스와 그 자식들을 정리한다
    if (Test-Path $PidFile) {
        $saved = Get-Content $PidFile -Raw | ConvertFrom-Json
        foreach ($prop in $saved.PSObject.Properties) {
            if ($prop.Value) { & taskkill /PID $prop.Value /T /F 2>$null | Out-Null }
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }
    # 2) 그래도 포트를 잡고 있는 프로세스(앱이 켠 것 포함)가 있으면 정리한다
    foreach ($s in $Servers) {
        $owners = Get-NetTCPConnection -LocalPort $s.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($owner in $owners) { & taskkill /PID $owner /T /F 2>$null | Out-Null }
    }
    Start-Sleep -Seconds 2
    foreach ($s in $Servers) {
        Write-Host ("[{0}] {1} (포트 {2})" -f $(if (Test-Port $s.Port) { '실패' } else { '꺼짐' }), $s.Name, $s.Port)
    }
}

function Show-Status {
    foreach ($s in $Servers) {
        $state = if (-not (Test-Port $s.Port)) { '꺼짐' } elseif (Test-Ready $s) { '켜짐(응답 정상)' } else { '켜짐(응답 없음)' }
        Write-Host ("{0,-6} 포트 {1} : {2}" -f $s.Name, $s.Port, $state)
    }
}

switch ($Action) {
    'status'  { Show-Status }
    'stop'    { Stop-All }
    'restart' { Stop-All; & $PSCommandPath 'start' }
    'start' {
        $pids = [ordered]@{}
        foreach ($s in $Servers) {
            $id = Start-Server $s
            if ($id) { $pids[$s.Key] = $id }
        }
        if ($pids.Count -gt 0) {
            # 이미 켜진 서버의 기록을 덮어쓰지 않도록 합쳐서 저장한다
            $merged = [ordered]@{}
            if (Test-Path $PidFile) {
                $old = Get-Content $PidFile -Raw | ConvertFrom-Json
                foreach ($prop in $old.PSObject.Properties) { $merged[$prop.Name] = $prop.Value }
            }
            foreach ($k in $pids.Keys) { $merged[$k] = $pids[$k] }
            $merged | ConvertTo-Json | Set-Content -Path $PidFile -Encoding UTF8
        }
        Write-Host ''
        foreach ($s in $Servers) { Wait-Server $s | Out-Null }
        Write-Host ''
        Show-Status
    }
}
