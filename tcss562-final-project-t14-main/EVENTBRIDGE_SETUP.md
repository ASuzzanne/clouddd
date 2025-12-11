# EventBridge Setup for TLQ Pipeline

## Rule 1: Trigger Transform on CSV Upload

### Create EventBridge Rule
```powershell
aws events put-rule `
  --name "S3-CSV-Upload-Trigger-Transform" `
  --event-pattern '{
    "source": ["aws.s3"],
    "detail-type": ["Object Created"],
    "detail": {
      "bucket": {
        "name": ["tcss562-suzzanne-data"]
      },
      "object": {
        "key": [{
          "suffix": ".csv"
        }, {
          "anything-but": {
            "suffix": ".transformed.csv"
          }
        }]
      }
    }
  }' `
  --description "Trigger TransformCSV Lambda when CSV file is uploaded to S3"
```

### Add Lambda Permission
```powershell
aws lambda add-permission `
  --function-name TransformCSV `
  --statement-id EventBridgeInvokeTransform `
  --action lambda:InvokeFunction `
  --principal events.amazonaws.com `
  --source-arn "arn:aws:events:us-west-2:110412263187:rule/S3-CSV-Upload-Trigger-Transform"
```

### Add Lambda as Target
```powershell
aws events put-targets `
  --rule S3-CSV-Upload-Trigger-Transform `
  --targets "Id"="1","Arn"="arn:aws:lambda:us-west-2:110412263187:function:TransformCSV"
```

---

## Rule 2: Trigger Load on Transformed CSV Upload

### Create EventBridge Rule
```powershell
aws events put-rule `
  --name "S3-Transformed-CSV-Trigger-Load" `
  --event-pattern '{
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
  }' `
  --description "Trigger CreateSQLiteDB Lambda when transformed CSV is uploaded"
```

### Add Lambda Permission
```powershell
aws lambda add-permission `
  --function-name CreateSQLiteDB `
  --statement-id EventBridgeInvokeLoad `
  --action lambda:InvokeFunction `
  --principal events.amazonaws.com `
  --source-arn "arn:aws:events:us-west-2:110412263187:rule/S3-Transformed-CSV-Trigger-Load"
```

### Add Lambda as Target
```powershell
aws events put-targets `
  --rule S3-Transformed-CSV-Trigger-Load `
  --targets "Id"="1","Arn"="arn:aws:lambda:us-west-2:110412263187:function:CreateSQLiteDB"
```

---

## Enable S3 Event Notifications to EventBridge

```powershell
aws s3api put-bucket-notification-configuration `
  --bucket tcss562-suzzanne-data `
  --notification-configuration '{
    "EventBridgeConfiguration": {}
  }'
```

---

## Testing the Pipeline

1. Upload a CSV file to S3:
```powershell
aws s3 cp test.csv s3://tcss562-suzzanne-data/test.csv
```

2. Check EventBridge triggered Transform:
```powershell
aws logs tail /aws/lambda/TransformCSV --follow
```

3. Verify transformed file created:
```powershell
aws s3 ls s3://tcss562-suzzanne-data/ | Select-String "transformed"
```

4. Check EventBridge triggered Load:
```powershell
aws logs tail /aws/lambda/CreateSQLiteDB --follow
```

5. Verify database created and CSV deleted:
```powershell
aws s3 ls s3://tcss562-suzzanne-data/databases/
aws s3 ls s3://tcss562-suzzanne-data/ | Select-String "transformed"
```

---

## Cleanup Commands

### Remove EventBridge Rules
```powershell
aws events remove-targets --rule S3-CSV-Upload-Trigger-Transform --ids 1
aws events delete-rule --name S3-CSV-Upload-Trigger-Transform

aws events remove-targets --rule S3-Transformed-CSV-Trigger-Load --ids 1
aws events delete-rule --name S3-Transformed-CSV-Trigger-Load
```

### Remove Lambda Permissions
```powershell
aws lambda remove-permission --function-name TransformCSV --statement-id EventBridgeInvokeTransform
aws lambda remove-permission --function-name CreateSQLiteDB --statement-id EventBridgeInvokeLoad
```
