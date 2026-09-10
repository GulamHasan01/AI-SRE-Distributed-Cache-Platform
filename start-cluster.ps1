# PowerShell script to build, launch, and test the Distributed Cache Platform cluster

param (
    [switch]$Docker,
    [switch]$BuildOnly,
    [switch]$Test,
    [switch]$DryRun,
    [switch]$Demo,
    [switch]$Interactive
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Distributed Self-Healing Cache Platform + AI SRE Layer " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ($BuildOnly) {
    Write-Host "`n[1/4] Building cache-service..." -ForegroundColor Yellow
    Push-Location ./cache-service; .\mvnw.cmd clean package -DskipTests; Pop-Location

    Write-Host "`n[2/4] Building gateway-service..." -ForegroundColor Yellow
    Push-Location ./gateway-service; .\mvnw.cmd clean package -DskipTests; Pop-Location

    Write-Host "`n[3/4] Building notification-service..." -ForegroundColor Yellow
    Push-Location ./notification-service; .\mvnw.cmd clean package -DskipTests; Pop-Location

    Write-Host "`n[4/4] Building iam-service..." -ForegroundColor Yellow
    Push-Location "./iam-service - updated"; .\mvnw.cmd clean package -DskipTests; Pop-Location

    Write-Host "`nAll microservices built successfully!" -ForegroundColor Green
    exit 0
}

if ($Demo) {
    Write-Host "`nLaunching Autonomous AI SRE Incident Response Demo..." -ForegroundColor Green
    $demoArgs = @()
    if ($DryRun) { $demoArgs += "--dry-run" }
    if ($Interactive) { $demoArgs += "--interactive" }
    python chaos\demo_runner.py @demoArgs
    exit 0
}

if ($Test) {
    Write-Host "`nExecuting E2E Cluster Integration Test Suite..." -ForegroundColor Yellow
    if ($DryRun) {
        python e2e_cluster_test.py --dry-run
    } else {
        python e2e_cluster_test.py
    }
    Write-Host "`nExecuting SRE Agent & MCP Unit Test Suite..." -ForegroundColor Yellow
    python -m pytest sre-cluster-mcp\tests sre-agent\tests -v
    exit 0
}

if ($Docker) {
    Write-Host "`nLaunching full Distributed Cache + AI SRE Cluster with Docker Compose..." -ForegroundColor Green
    docker-compose up --build -d
    Write-Host "`nCluster started!" -ForegroundColor Green
    Write-Host "  - Gateway API:      http://localhost:8080"
    Write-Host "  - Cache Node 1:     http://localhost:8081"
    Write-Host "  - Cache Node 2:     http://localhost:8082"
    Write-Host "  - Cache Node 3:     http://localhost:8083"
    Write-Host "  - Notification:     http://localhost:8084"
    Write-Host "  - IAM Service:      http://localhost:8085"
    Write-Host "  - Prometheus:       http://localhost:9091"
    Write-Host "  - SRE MCP Server:   http://localhost:8000"
    Write-Host "  - SRE AI Agent:     http://localhost:9090"
    Write-Host "  - Web Dashboard:    http://localhost:3000"
} else {
    Write-Host "`nUsage:" -ForegroundColor Yellow
    Write-Host "  .\start-cluster.ps1 -BuildOnly               # Compiles all microservices"
    Write-Host "  .\start-cluster.ps1 -Docker                  # Launches entire cluster via docker-compose"
    Write-Host "  .\start-cluster.ps1 -Test -DryRun            # Runs synthetic E2E integration test suite"
    Write-Host "  .\start-cluster.ps1 -Demo -DryRun            # Runs automated 15-60s AI SRE incident demo"
    Write-Host "  .\start-cluster.ps1 -Demo -Interactive       # Runs live interactive demo with HITL prompt"
}
