<#
.SYNOPSIS
   停止 OAStem 的 Redis 主从 + 哨兵演练集群。

.DESCRIPTION
    只停止本脚本启动的那 5 个端口（6381/6382/26381/26382/26383），
    不会碰 6379 —— 开发机上它可能承载着其它项目的数据。
#>
param(
    [switch]$PurgeData
)

$ErrorActionPreference = "Continue"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtime = Join-Path $here "runtime"
$ports = 6381, 6382, 26381, 26382, 26383

foreach ($port in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        $procId = ($conn | Select-Object -First 1).OwningProcess
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "  [停止] 端口 $port (PID $procId)" -ForegroundColor Yellow
        } catch {
            Write-Host "  [失败] 端口 $port (PID $procId)：$($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "  [空] 端口 $port 未在监听" -ForegroundColor DarkGray
    }
}

if ($PurgeData -and (Test-Path $runtime)) {
    # 只清 runtime，模板文件不受影响
    Remove-Item -Recurse -Force $runtime
    Write-Host "  [清理] 已删除 runtime/（含数据与日志）" -ForegroundColor Yellow
}
