$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeDir = Join-Path $projectRoot '.run'
$stopped = 0

function Get-ProcessTreeIds([int]$rootId, $allProcesses) {
    $ids = New-Object System.Collections.Generic.List[int]
    $children = @($allProcesses | Where-Object { [int]$_.ParentProcessId -eq $rootId })
    foreach ($child in $children) {
        foreach ($descendantId in (Get-ProcessTreeIds ([int]$child.ProcessId) $allProcesses)) {
            $ids.Add($descendantId)
        }
    }
    $ids.Add($rootId)
    return $ids.ToArray()
}

$processSnapshot = @(Get-CimInstance Win32_Process -ErrorAction Stop)

foreach ($name in @('frontend', 'backend')) {
    $pidPath = Join-Path $runtimeDir ($name + '.pid')
    if (-not (Test-Path -LiteralPath $pidPath)) { continue }
    $rawPid = (Get-Content -LiteralPath $pidPath -Raw).Trim()
    $processId = 0
    if (-not [int]::TryParse($rawPid, [ref]$processId)) {
        Remove-Item -LiteralPath $pidPath -Force
        Write-Warning "$name 的进程记录无效，已清理记录。"
        continue
    }

    $process = $processSnapshot | Where-Object { [int]$_.ProcessId -eq $processId } | Select-Object -First 1
    if (-not $process) {
        Remove-Item -LiteralPath $pidPath -Force
        Write-Host "$name 已经停止。"
        continue
    }
    $commandLine = [string]$process.CommandLine
    if ($commandLine -notlike "*$projectRoot*") {
        Write-Warning "$name 的进程号已被其他程序复用；为避免误关程序，没有结束该进程。"
        Remove-Item -LiteralPath $pidPath -Force
        continue
    }

    $treeIds = Get-ProcessTreeIds $processId $processSnapshot
    foreach ($treeId in $treeIds) {
        Stop-Process -Id $treeId -Force -ErrorAction SilentlyContinue
    }
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $remaining = @($treeIds | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
        if ($remaining.Count -eq 0) { break }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    if ($remaining.Count -eq 0) {
        Write-Host "已停止 $name（进程 $processId）。"
    } else {
        Write-Warning "$name 的部分进程在5秒内未退出：$($remaining -join ', ')。请检查服务日志。"
    }
    $stopped++
}

if ($stopped -eq 0) { Write-Host '没有正在运行且由本项目脚本启动的服务。' }
