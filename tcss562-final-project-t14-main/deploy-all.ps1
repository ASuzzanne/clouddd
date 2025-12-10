# Deploy All TLQ Pipeline Lambda Functions
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Deploying TLQ Pipeline to AWS Lambda" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Build the project first
Write-Host "`nStep 1: Building project..." -ForegroundColor Yellow
mvn clean package
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "`nStep 2: Deploying Transform function..." -ForegroundColor Yellow
.\deploy-transform.ps1

Write-Host "`nStep 3: Updating Load function..." -ForegroundColor Yellow
.\deploy.ps1

Write-Host "`nStep 4: Deploying Query function..." -ForegroundColor Yellow
.\deploy-query.ps1

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "All functions deployed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

Write-Host "`nDeployed Functions:" -ForegroundColor Cyan
Write-Host "1. TransformCSV - Service 1: Transform"
Write-Host "2. CreateSQLiteDB - Service 2: Load"
Write-Host "3. QuerySQLite - Service 3: Query"
