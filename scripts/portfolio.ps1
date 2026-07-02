param(
    [ValidateSet(
        "status",
        "help",
        "env",
        "up",
        "down",
        "restart",
        "restart-api",
        "restart-web",
        "logs",
        "logs-api",
        "logs-web",
        "db-up",
        "db-restart",
        "docker-up",
        "docker-down",
        "docker-restart",
        "docker-logs",
        "docker-logs-api",
        "docker-logs-web",
        "docker-logs-db"
    )]
    [string]$Action = "status"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RunDir = Join-Path $Root ".run"
$LogDir = Join-Path $Root ".logs"

function Ensure-Dirs {
    New-Item -ItemType Directory -Force -Path $RunDir, $LogDir | Out-Null
}

function Get-PidFile([string]$Name) {
    Join-Path $RunDir "$Name.pid"
}

function Get-LogFile([string]$Name, [string]$Stream) {
    Join-Path $LogDir "$Name.$Stream.log"
}

function Stop-PidFile([string]$Name) {
    $pidFile = Get-PidFile $Name
    if (Test-Path $pidFile) {
        $processId = [int](Get-Content $pidFile -Raw)
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process) {
            taskkill /PID $processId /T /F | Out-Null
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
}

function Stop-Api {
    Stop-PidFile "api"
    Get-CimInstance Win32_Process |
        Where-Object {
            $_.ProcessId -ne $PID -and
            $_.CommandLine -and
            $_.CommandLine -like "*$Root*" -and
            (
                $_.CommandLine -like "*spring-boot:run*" -or
                $_.CommandLine -like "*dev.sg.portfolio.PortfolioApiApplication*"
            )
        } |
        ForEach-Object {
            taskkill /PID $_.ProcessId /T /F | Out-Null
        }
}

function Stop-Web {
    Stop-PidFile "web"
    Get-CimInstance Win32_Process |
        Where-Object {
            $_.ProcessId -ne $PID -and
            $_.CommandLine -and
            $_.CommandLine -like "*$Root*" -and
            (
                $_.CommandLine -like "*dev:web*" -or
                $_.CommandLine -like "*vite.js*--host*0.0.0.0*--port*5173*"
            )
        } |
        ForEach-Object {
            taskkill /PID $_.ProcessId /T /F | Out-Null
        }
}

function Stop-Local {
    Stop-PidFile "all"
    Stop-Api
    Stop-Web
    Get-CimInstance Win32_Process |
        Where-Object {
            $_.ProcessId -ne $PID -and
            $_.CommandLine -and
            $_.CommandLine -like "*$Root*" -and
            $_.CommandLine -like "*npm run dev*"
        } |
        ForEach-Object {
            taskkill /PID $_.ProcessId /T /F | Out-Null
        }
}

function Show-Help {
    Write-Host "Uso: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio.ps1 <accion>"
    Write-Host ""
    Write-Host "Acciones locales:"
    Write-Host "  status        Muestra puertos usados"
    Write-Host "  env           Lista variables .env sin revelar valores"
    Write-Host "  up            Levanta backend y frontend local"
    Write-Host "  down          Detiene backend y frontend local"
    Write-Host "  restart       Reinicia backend y frontend local"
    Write-Host "  restart-api   Reinicia solo backend"
    Write-Host "  restart-web   Reinicia solo frontend"
    Write-Host "  logs          Muestra ultimas lineas de logs locales"
    Write-Host "  logs-api      Sigue logs del backend local"
    Write-Host "  logs-web      Sigue logs del frontend local"
    Write-Host ""
    Write-Host "Acciones Docker:"
    Write-Host "  db-up | db-restart"
    Write-Host "  docker-up | docker-down | docker-restart"
    Write-Host "  docker-logs | docker-logs-api | docker-logs-web | docker-logs-db"
}

function Start-Api {
    Ensure-Dirs
    Stop-Api
    $out = Get-LogFile "api" "out"
    $err = Get-LogFile "api" "err"
    $command = "Set-Location -LiteralPath '$Root\backend'; .\mvnw.cmd spring-boot:run"
    $process = Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) `
        -RedirectStandardOutput $out `
        -RedirectStandardError $err `
        -PassThru `
        -WindowStyle Hidden
    Set-Content -LiteralPath (Get-PidFile "api") -Value $process.Id
    Write-Host "API started: PID $($process.Id), logs $out"
}

function Start-Web {
    Ensure-Dirs
    Stop-Web
    $out = Get-LogFile "web" "out"
    $err = Get-LogFile "web" "err"
    $command = "Set-Location -LiteralPath '$Root'; npm run dev:web"
    $process = Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) `
        -RedirectStandardOutput $out `
        -RedirectStandardError $err `
        -PassThru `
        -WindowStyle Hidden
    Set-Content -LiteralPath (Get-PidFile "web") -Value $process.Id
    Write-Host "Web started: PID $($process.Id), logs $out"
}

function Start-Local {
    Start-Api
    Start-Web
    Write-Host "Local URLs:"
    Write-Host "  Frontend: http://localhost:5173"
    Write-Host "  Backend:  http://localhost:8787/api/portfolio/health"
}

function Show-LogTail([string]$Name, [string]$Stream, [int]$Tail = 80) {
    $path = Get-LogFile $Name $Stream
    if (Test-Path $path) {
        Write-Host ""
        Write-Host "==> $path"
        Get-Content -LiteralPath $path -Tail $Tail
    } else {
        Write-Host ""
        Write-Host "==> $path (no existe todavia)"
    }
}

function Show-LocalLogs {
    Show-LogTail "api" "out" 60
    Show-LogTail "api" "err" 60
    Show-LogTail "web" "out" 60
    Show-LogTail "web" "err" 60
}

function Watch-ServiceLogs([string]$Name) {
    $out = Get-LogFile $Name "out"
    $err = Get-LogFile $Name "err"
    if (!(Test-Path $out)) {
        New-Item -ItemType File -Force -Path $out | Out-Null
    }
    if (!(Test-Path $err)) {
        New-Item -ItemType File -Force -Path $err | Out-Null
    }

    Write-Host "Siguiendo logs de $Name. Ctrl+C para salir."
    Get-Content -LiteralPath $out, $err -Tail 120 -Wait
}

function Show-Status {
    $ports = @(5173, 8787, 5432, 5433, 8080)
    foreach ($port in $ports) {
        $listeners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($listeners) {
            $owners = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
            Write-Host "Port ${port}: listening (PID $($owners -join ', '))"
        } else {
            Write-Host "Port ${port}: free"
        }
    }
}

function Show-Env {
    $envFile = Join-Path $Root ".env"
    if (!(Test-Path $envFile)) {
        Write-Host ".env not found"
        return
    }

    Get-Content $envFile |
        Where-Object { $_ -match "^\s*([^#=]+)=(.*)$" } |
        ForEach-Object {
            $name = $Matches[1].Trim()
            $value = $Matches[2]
            [PSCustomObject]@{
                Name = $name
                HasValue = [bool]$value
                Length = $value.Length
            }
        } |
        Format-Table -AutoSize
}

function Run-Compose([string[]]$ComposeArgs) {
    Push-Location $Root
    try {
        & docker compose @ComposeArgs
    } finally {
        Pop-Location
    }
}

switch ($Action) {
    "help" { Show-Help }
    "status" { Show-Status }
    "env" { Show-Env }
    "up" { Start-Local }
    "down" { Stop-Local; Write-Host "Local stack stopped." }
    "restart" { Stop-Local; Start-Local }
    "restart-api" { Start-Api }
    "restart-web" { Start-Web }
    "logs" { Show-LocalLogs }
    "logs-api" { Watch-ServiceLogs "api" }
    "logs-web" { Watch-ServiceLogs "web" }
    "db-up" { Run-Compose @("up", "-d", "db") }
    "db-restart" { Run-Compose @("restart", "db") }
    "docker-up" { Run-Compose @("up", "-d", "--build") }
    "docker-down" { Run-Compose @("down") }
    "docker-restart" { Run-Compose @("restart") }
    "docker-logs" { Run-Compose @("logs", "-f") }
    "docker-logs-api" { Run-Compose @("logs", "-f", "backend") }
    "docker-logs-web" { Run-Compose @("logs", "-f", "frontend") }
    "docker-logs-db" { Run-Compose @("logs", "-f", "db") }
}
