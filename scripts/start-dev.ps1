$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendDir = Join-Path $projectRoot 'backend'
$frontendDir = Join-Path $projectRoot 'frontend'
$runtimeDir = Join-Path $projectRoot '.run'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

function Test-CommandAvailable([string]$name) {
    return $null -ne (Get-Command $name -ErrorAction SilentlyContinue)
}

foreach ($command in @('mvn', 'java', 'node')) {
    if (-not (Test-CommandAvailable $command)) {
        throw "未找到 $command。请先安装并配置环境变量。"
    }
}
if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    throw '前端依赖尚未安装。请先在 frontend 目录运行 npm.cmd ci。'
}

foreach ($port in @(8080, 5173)) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        throw "端口 $port 已被进程 $($listener.OwningProcess) 占用。请先确认它是否为本项目服务，再运行启动脚本。"
    }
}

$services = @(
    @{ Name = 'backend'; Dir = $backendDir; Command = 'mvn spring-boot:run'; Log = 'backend' },
    @{ Name = 'frontend'; Dir = $frontendDir; Command = 'npm.cmd run dev -- --host 127.0.0.1'; Log = 'frontend' }
)
$started = @()
try {
    foreach ($service in $services) {
        $logPath = Join-Path $runtimeDir ($service.Log + '.out.log')
        $errorLogPath = Join-Path $runtimeDir ($service.Log + '.err.log')
        $cmdLine = 'cd /d "{0}" && {1}' -f $service.Dir, $service.Command
        $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/s', '/c', "`"$cmdLine`"") `
            -WorkingDirectory $service.Dir -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $logPath -RedirectStandardError $errorLogPath
        $pidPath = Join-Path $runtimeDir ($service.Name + '.pid')
        Set-Content -LiteralPath $pidPath -Value $process.Id -Encoding ASCII
        $started += @{ Name = $service.Name; Id = $process.Id; PidPath = $pidPath }
        Write-Host "已启动 $($service.Name)（进程 $($process.Id)），日志：$logPath / $errorLogPath"
    }
} catch {
    foreach ($service in $started) {
        & taskkill.exe /PID $service.Id /T /F 2>$null | Out-Null
        Remove-Item -LiteralPath $service.PidPath -Force -ErrorAction SilentlyContinue
    }
    throw
}

Write-Host ''
Write-Host '前端地址：http://localhost:5173'
Write-Host '后端地址：http://localhost:8080'
Write-Host '请确保 MySQL 服务已启动。关闭服务请双击 stop-dev.bat。'
