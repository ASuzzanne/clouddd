# Deploy QuerySQLite Lambda Function
$FUNCTION_NAME = "QuerySQLite"
$HANDLER = "lambda.QuerySQLite::handleRequest"
$ROLE_ARN = "arn:aws:iam::110412263187:role/lambda-s3-sqlite-role"
$JAR_FILE = "target\lambda_test-1.0-SNAPSHOT.jar"
$RUNTIME = "java11"
$TIMEOUT = 300
$MEMORY = 512

Write-Host "Deploying $FUNCTION_NAME Lambda function..." -ForegroundColor Green

# Check if function exists
$functionExists = aws lambda get-function --function-name $FUNCTION_NAME 2>$null

if ($LASTEXITCODE -eq 0) {
    Write-Host "Function exists. Updating..." -ForegroundColor Yellow
    aws lambda update-function-code `
        --function-name $FUNCTION_NAME `
        --zip-file "fileb://$JAR_FILE"
    
    aws lambda update-function-configuration `
        --function-name $FUNCTION_NAME `
        --handler $HANDLER `
        --runtime $RUNTIME `
        --timeout $TIMEOUT `
        --memory-size $MEMORY
} else {
    Write-Host "Creating new function..." -ForegroundColor Yellow
    aws lambda create-function `
        --function-name $FUNCTION_NAME `
        --runtime $RUNTIME `
        --role $ROLE_ARN `
        --handler $HANDLER `
        --zip-file "fileb://$JAR_FILE" `
        --timeout $TIMEOUT `
        --memory-size $MEMORY `
        --description "Service 3: Query SQLite - Execute SQL queries with /tmp caching"
}

Write-Host "Deployment complete!" -ForegroundColor Green
