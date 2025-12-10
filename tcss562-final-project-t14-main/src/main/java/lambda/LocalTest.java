package lambda;

import java.io.File;

public class LocalTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Local SQLite DB Creation Test ===\n");

        // Use the test.csv file
        File csvFile = new File("test.csv");
        
        if (!csvFile.exists()) {
            System.out.println("ERROR: test.csv not found in current directory!");
            System.exit(1);
        }

        System.out.println("✓ Found test.csv (" + csvFile.length() + " bytes)\n");

        CreateSQLiteDB handler = new CreateSQLiteDB();
        
        try {
            // Create a simple logger context
            SimpleLogger logger = new SimpleLogger();
            
            File dbFile = handler.createSQLiteDB(csvFile, "test.db", "sales_records", logger);
            
            System.out.println("\n✓ Database created successfully!");
            System.out.println("✓ Location: " + dbFile.getAbsolutePath());
            System.out.println("✓ Size: " + dbFile.length() + " bytes");
            System.out.println("\n=== TEST PASSED ===");
            
        } catch (Exception e) {
            System.out.println("\n✗ ERROR during database creation:");
            e.printStackTrace();
            System.out.println("\n=== TEST FAILED ===");
            System.exit(1);
        }
    }
}

class SimpleLogger implements com.amazonaws.services.lambda.runtime.Context {
    private com.amazonaws.services.lambda.runtime.LambdaLogger logger = new com.amazonaws.services.lambda.runtime.LambdaLogger() {
        @Override
        public void log(String message) {
            System.out.print(message);
        }
    };

    @Override
    public String getAwsRequestId() { return "local-test"; }
    @Override
    public String getLogGroupName() { return "local"; }
    @Override
    public String getLogStreamName() { return "local"; }
    @Override
    public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }
    @Override
    public int getRemainingTimeInMillis() { return 300000; }
    @Override
    public int getMemoryLimitInMB() { return 128; }
    @Override
    public com.amazonaws.services.lambda.runtime.LambdaLogger getLogger() { return logger; }
    @Override
    public String getFunctionName() { return "local-test"; }
    @Override
    public String getFunctionVersion() { return "1"; }
    @Override
    public String getInvokedFunctionArn() { return "arn:aws:lambda:local:123456789012:function:local-test"; }
    @Override
    public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }
}
