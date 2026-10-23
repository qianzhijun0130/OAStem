<#
.SYNOPSIS
   启动 OAStem 的 Redis 主从 + 哨兵演练集群（Windows）。

.DESCRIPTION
    端口规划（刻意避开 6379，开发机上它可能被其它项目占用）：

        6381  master
        6382  replica (slaveof 6381)
        26381 / 26382 / 26383  sentinel  x3，quorum = 2

    配置从 *.conf.template 复制到 runtime/ 后启动，template 本身不被修改；
    Sentinel 运行中会重写自己的配置文件，所以 runtime/ 已在 .gitignore 中。

.PARAMETER RedisHome
    Redis 安装目录（含 redis-server.exe）。默认读 REDIS_HOME 环境变量，
    再退回 D:\Program Files\Redis。

.EXAMPLE
    ./start-ha.ps1
    ./start-ha.ps1 -RedisHome "C:\Redis"
#>
param(
    [string]$RedisHome = $(if ($env:REDIS_HOME) { $env:REDIS_HOME } else { "D:\Program Files\Redis" })
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtime = Join-Path $here "runtime"
$dataDir = Join-Path $runtime "data"
$logDir = Join-Path $runtime "logs"
$exe = Join-Path $RedisHome "redis-server.exe"

if (-not (Test-Path $exe)) {
    throw "未找到 redis-server.exe：$exe（用 -RedisHome 指定 Redis 安装目录）"
}
New-Item -ItemType Directory -Force -Path $dataDir, $logDir | Out-Null

function Test-PortBusy([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Start-RedisInstance {
    param([string]$Name, [string]$ConfPath, [int]$Port)

    if (Test-PortBusy $Port) {
        Write-Host "  [跳过] $Name 端口 $Port 已在监听（可能已在运行）" -ForegroundColor Yellow
        return
    }
    # 工作目录设为 runtime：配置里的相对路径（dir/logfile）才是确定的
    Start-Process -FilePath $exe -ArgumentList @($ConfPath) `
        -WorkingDirectory $runtime -WindowStyle Hidden | Out-Null
    Start-Sleep -Milliseconds 700
    Write-Host "  [启动] $Name -> 端口 $Port" -ForegroundColor Green
}

# ── 1. 生成运行期配置 ───────────────────────────────────────
# Redis 会先 chdir 到 dir，再按相对路径找 logfile：相对路径会被解析到 data/logs 下而失败，
# 所以模板用 _RUNTIME_ 占位，这里替换成绝对路径（统一正斜杠，Windows 版同样接受）。
#
# 按「行数组」读写，而不是 Get-Content -Raw + Set-Content：
# 一次性字符串在 ASCII 编码下会把中文注释变成 '?'，且行边界容易丢，曾导致
# `dir` / `sentinel monitor` 被并进上一行注释里，Redis 直接读不到关键指令。
function Expand-Template([string]$tplPath, [string]$outPath, [hashtable]$vars) {
    $rt = $runtime.Replace('\', '/')
    $lines = [System.IO.File]::ReadAllLines($tplPath, [System.Text.UTF8Encoding]::new($false)) |
        ForEach-Object {
            $line = $_.Replace("_RUNTIME_", $rt)
            foreach ($k in $vars.Keys) { $line = $line.Replace($k, $vars[$k]) }
            $line
        }
    # 不用 BOM：Redis 会把 BOM 当成第一行的内容，导致首行指令解析失败
    [System.IO.File]::WriteAllLines($outPath, $lines, [System.Text.UTF8Encoding]::new($false))
}

Expand-Template (Join-Path $here "master.conf.template")  (Join-Path $runtime "master-6381.conf")  @{}
Expand-Template (Join-Path $here "replica.conf.template") (Join-Path $runtime "replica-6382.conf") @{}
foreach ($p in 26381, 26382, 26383) {
    Expand-Template (Join-Path $here "sentinel.conf.template") (Join-Path $runtime "sentinel-$p.conf") @{ "_PORT_" = "$p" }
}

# ── 2. 启动主从 ─────────────────────────────────────────────
Write-Host "启动 Redis 主从..." -ForegroundColor Cyan
Start-RedisInstance "master"  (Join-Path $runtime "master-6381.conf")  6381
Start-RedisInstance "replica" (Join-Path $runtime "replica-6382.conf") 6382

# ── 3. 启动哨兵（必须在主从就绪后，否则首次发现的拓扑可能不全）─────
Write-Host "启动哨兵 x3..." -ForegroundColor Cyan
foreach ($p in 26381, 26382, 26383) {
    if (Test-PortBusy $p) {
        Write-Host "  [跳过] sentinel 端口 $p 已在监听" -ForegroundColor Yellow
        continue
    }
    Start-Process -FilePath $exe `
        -ArgumentList @((Join-Path $runtime "sentinel-$p.conf"), "--sentinel") `
        -WorkingDirectory $runtime -WindowStyle Hidden | Out-Null
    Start-Sleep -Milliseconds 500
    Write-Host "  [启动] sentinel -> 端口 $p" -ForegroundColor Green
}

Start-Sleep -Seconds 2
Write-Host ""
Write-Host "集群已就绪，可用以下命令核对：" -ForegroundColor Cyan
Write-Host "  redis-cli -p 6381 INFO replication     # 应看到 role:master 且 connected_slaves:1"
Write-Host "  redis-cli -p 26381 SENTINEL MASTER oastem-master"
Write-Host "  应用侧启用哨兵：--spring.profiles.active=redis-ha"
Write-Host "  停止集群：./stop-ha.ps1"
