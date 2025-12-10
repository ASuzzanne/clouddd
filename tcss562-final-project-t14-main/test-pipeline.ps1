# Complete TLQ Pipeline Test Script
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "TESTING TLQ PIPELINE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "[1/3] Transform CSV..." -ForegroundColor Yellow
aws lambda invoke --function-name TransformCSV --cli-binary-format raw-in-base64-out --payload file://test-transform-payload.json response.json | Out-Null
Get-Content response.json | ConvertFrom-Json | Select status,destLocation

Write-Host "[2/3] Load into SQLite..." -ForegroundColor Yellow
aws lambda invoke --function-name CreateSQLiteDB --cli-binary-format raw-in-base64-out --payload file://test-load-payload.json response.json | Out-Null
Get-Content response.json | ConvertFrom-Json | Select status,dbS3Location

Write-Host "[3/3] Query Database - Count..." -ForegroundColor Yellow
aws lambda invoke --function-name QuerySQLite --cli-binary-format raw-in-base64-out --payload file://test-query-payload.json response.json | Out-Null
Get-Content response.json | ConvertFrom-Json

Write-Host "========================================" -ForegroundColor Green
Write-Host "PIPELINE TEST COMPLETE!" -ForegroundColor Green
