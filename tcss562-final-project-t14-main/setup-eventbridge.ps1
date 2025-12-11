# Setup EventBridge Rules for TLQ Pipeline Automation

Write-Host "`n=====================================" -ForegroundColor Cyan
Write-Host "EventBridge Setup for TLQ Pipeline" -ForegroundColor Cyan
Write-Host "=====================================`n" -ForegroundColor Cyan

# Step 1: Enable S3 EventBridge notifications
Write-Host "Step 1: Enabling S3 EventBridge notifications..." -ForegroundColor Yellow
aws s3api put-bucket-notification-configuration `
  --bucket tcss562-suzzanne-data `
  --notification-configuration '{"EventBridgeConfiguration": {}}'

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ S3 EventBridge enabled" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to enable S3 EventBridge" -ForegroundColor Red
    exit 1
}

# Step 2: Create Rule 1 - Trigger Transform on CSV upload
Write-Host "`nStep 2: Creating EventBridge rule for Transform..." -ForegroundColor Yellow
$eventPattern1 = @'
{
  "source": ["aws.s3"],
  "detail-type": ["Object Created"],
  "detail": {
    "bucket": {
      "name": ["tcss562-suzzanne-data"]
    },
    "object": {
      "key": [{
        "suffix": ".csv"
      }]
    }
  }
}
'@ -replace "`n", "" -replace "`r", ""

aws events put-rule `
  --name "S3-CSV-Upload-Trigger-Transform" `
  --event-pattern $eventPattern1 `
  --description "Trigger TransformCSV when CSV uploaded" `
  --region us-west-2

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ EventBridge rule created: S3-CSV-Upload-Trigger-Transform" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to create EventBridge rule" -ForegroundColor Red
}

# Step 3: Add Lambda permission for Transform
Write-Host "`nStep 3: Adding Lambda permission for Transform..." -ForegroundColor Yellow
aws lambda add-permission `
  --function-name TransformCSV `
  --statement-id EventBridgeInvokeTransform `
  --action lambda:InvokeFunction `
  --principal events.amazonaws.com `
  --source-arn "arn:aws:events:us-west-2:110412263187:rule/S3-CSV-Upload-Trigger-Transform" `
  --region us-west-2 2>$null

Write-Host "✓ Lambda permission added for Transform" -ForegroundColor Green

# Step 4: Add Transform Lambda as target
Write-Host "`nStep 4: Connecting Transform Lambda to EventBridge..." -ForegroundColor Yellow
aws events put-targets `
  --rule S3-CSV-Upload-Trigger-Transform `
  --targets "Id"="1","Arn"="arn:aws:lambda:us-west-2:110412263187:function:TransformCSV" `
  --region us-west-2

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Transform Lambda connected to EventBridge" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to add target" -ForegroundColor Red
}

# Step 5: Create Rule 2 - Trigger Load on transformed CSV
Write-Host "`nStep 5: Creating EventBridge rule for Load..." -ForegroundColor Yellow
$eventPattern2 = @'
{
  "source": ["aws.s3"],
  "detail-type": ["Object Created"],
  "detail": {
    "bucket": {
      "name": ["tcss562-suzzanne-data"]
    },
    "object": {
      "key": [{
        "suffix": ".transformed.csv"
      }]
    }
  }
}
'@ -replace "`n", "" -replace "`r", ""

aws events put-rule `
  --name "S3-Transformed-CSV-Trigger-Load" `
  --event-pattern $eventPattern2 `
  --description "Trigger CreateSQLiteDB when transformed CSV uploaded" `
  --region us-west-2

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ EventBridge rule created: S3-Transformed-CSV-Trigger-Load" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to create EventBridge rule" -ForegroundColor Red
}

# Step 6: Add Lambda permission for Load
Write-Host "`nStep 6: Adding Lambda permission for Load..." -ForegroundColor Yellow
aws lambda add-permission `
  --function-name CreateSQLiteDB `
  --statement-id EventBridgeInvokeLoad `
  --action lambda:InvokeFunction `
  --principal events.amazonaws.com `
  --source-arn "arn:aws:events:us-west-2:110412263187:rule/S3-Transformed-CSV-Trigger-Load" `
  --region us-west-2 2>$null

Write-Host "✓ Lambda permission added for Load" -ForegroundColor Green

# Step 7: Add Load Lambda as target
Write-Host "`nStep 7: Connecting Load Lambda to EventBridge..." -ForegroundColor Yellow
aws events put-targets `
  --rule S3-Transformed-CSV-Trigger-Load `
  --targets "Id"="1","Arn"="arn:aws:lambda:us-west-2:110412263187:function:CreateSQLiteDB" `
  --region us-west-2

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Load Lambda connected to EventBridge" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to add target" -ForegroundColor Red
}

Write-Host "`n=====================================" -ForegroundColor Green
Write-Host "EventBridge Setup Complete!" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Green

Write-Host "`nPIPELINE FLOW:" -ForegroundColor Cyan
Write-Host "  1. Upload CSV to S3" -ForegroundColor White
Write-Host "  2. EventBridge triggers Transform Lambda" -ForegroundColor White
Write-Host "  3. Transform creates .transformed.csv" -ForegroundColor White
Write-Host "  4. EventBridge triggers Load Lambda" -ForegroundColor White
Write-Host "  5. Load creates .db and deletes .transformed.csv" -ForegroundColor White

Write-Host "`nTEST THE PIPELINE:" -ForegroundColor Cyan
Write-Host "  aws s3 cp test.csv s3://tcss562-suzzanne-data/test-auto.csv" -ForegroundColor Gray
Write-Host "`nMONITOR LOGS:" -ForegroundColor Cyan
Write-Host "  aws logs tail /aws/lambda/TransformCSV --follow" -ForegroundColor Gray
Write-Host "  aws logs tail /aws/lambda/CreateSQLiteDB --follow" -ForegroundColor Gray
