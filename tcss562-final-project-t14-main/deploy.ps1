$pythonCmd = "C:/Users/suzza/AppData/Local/Programs/Python/Python312/python.exe"
$accountId = "110412263187"
$roleName = "lambda-s3-sqlite-role"
$bucketName = "tcss562-suzzanne-data"

Write-Host "AWS Lambda Deployment" -ForegroundColor Green

Write-Host "Step 1: Creating IAM role..." -ForegroundColor Yellow
$policyDoc = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "lambda.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
"@

& $pythonCmd -m awscli iam create-role --role-name $roleName --assume-role-policy-document $policyDoc 2>&1 | Out-Null
Write-Host "Done" -ForegroundColor Green

Write-Host "Step 2: Attaching S3 policy..." -ForegroundColor Yellow
& $pythonCmd -m awscli iam attach-role-policy --role-name $roleName --policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess 2>&1 | Out-Null
Write-Host "Done" -ForegroundColor Green

Write-Host "Step 3: Waiting 10 seconds..." -ForegroundColor Yellow
Start-Sleep -Seconds 10
Write-Host "Done" -ForegroundColor Green

Write-Host "Step 4: Deploying Lambda..." -ForegroundColor Yellow
& $pythonCmd -m awscli lambda create-function --function-name CreateSQLiteDB --runtime java8 --role "arn:aws:iam::${accountId}:role/${roleName}" --handler lambda.CreateSQLiteDB --zip-file fileb://target/lambda_test-1.0-SNAPSHOT.jar --timeout 60 --memory-size 512 2>&1 | Out-Null
Write-Host "Done" -ForegroundColor Green

Write-Host "Step 5: Creating S3 bucket..." -ForegroundColor Yellow
& $pythonCmd -m awscli s3 mb "s3://${bucketName}" --region us-west-2 2>&1 | Out-Null
Write-Host "Done" -ForegroundColor Green

Write-Host "Step 6: Uploading CSV..." -ForegroundColor Yellow
& $pythonCmd -m awscli s3 cp test.csv "s3://${bucketName}/test.csv" 2>&1 | Out-Null
Write-Host "Done" -ForegroundColor Green

Write-Host "Step 7: Invoking Lambda..." -ForegroundColor Yellow
$payload = "{`"bucket`":`"${bucketName}`",`"key`":`"test.csv`",`"dbName`":`"sales.db`",`"tableName`":`"sales_records`"}"
& $pythonCmd -m awscli lambda invoke --function-name CreateSQLiteDB --payload $payload response.json 2>&1 | Out-Null
Write-Host "Done" -ForegroundColor Green

Write-Host ""
Write-Host "Response:" -ForegroundColor Cyan
Get-Content response.json

Write-Host ""
Write-Host "Deployment Complete" -ForegroundColor Green
