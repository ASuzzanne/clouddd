package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import java.io.*;
import java.sql.*;
import java.util.HashMap;

public class CreateSQLiteDB implements RequestHandler<HashMap<String, Object>, HashMap<String, Object>> {

    @Override
    public HashMap<String, Object> handleRequest(HashMap<String, Object> request, Context context) {
        HashMap<String, Object> response = new HashMap<>();

        try {
            String bucket = (String) request.get("bucket");
            String key = (String) request.get("key");
            String dbName = (String) request.getOrDefault("dbName", "tlq.db");
            String tableName = (String) request.getOrDefault("tableName", "sales_records");

            context.getLogger().log("Downloading file from S3: " + bucket + "/" + key + "\n");

            File downloaded = downloadFromS3(bucket, key, context);
            long fileSize = downloaded.length();
            context.getLogger().log("Downloaded file size: " + fileSize + " bytes\n");
            
            File sqliteDB = createSQLiteDB(downloaded, dbName, tableName, context);
            
            // Upload SQLite database back to S3
            String dbS3Key = "databases/" + dbName;
            uploadToS3(bucket, dbS3Key, sqliteDB, context);

            response.put("status", "success");
            response.put("dbPath", sqliteDB.getAbsolutePath());
            response.put("dbS3Location", "s3://" + bucket + "/" + dbS3Key);
            response.put("message", "SQLite database created and uploaded to S3 successfully.");
            response.put("fileSizeBytes", fileSize);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    /** Download file from S3 */
    private File downloadFromS3(String bucket, String key, Context context) throws IOException {
        AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();

        context.getLogger().log("Getting object from S3: " + bucket + "/" + key + "\n");
        S3Object object = s3.getObject(bucket, key);
        InputStream input = object.getObjectContent();

        File temp = File.createTempFile("s3file", ".tmp");
        FileOutputStream fos = new FileOutputStream(temp);

        byte[] buffer = new byte[4096];
        int length;
        long bytesRead = 0;

        while ((length = input.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
            bytesRead += length;
        }

        context.getLogger().log("Successfully read " + bytesRead + " bytes from S3\n");

        fos.close();
        input.close();
        return temp;
    }

    /** Create SQLite DB and load CSV */
    public File createSQLiteDB(File csvFile, String dbName, String tableName, Context context) throws Exception {
        File dbFile = new File("/tmp/" + dbName);
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        context.getLogger().log("Creating SQLite database at: " + dbFile.getAbsolutePath() + "\n");

        Connection conn = DriverManager.getConnection(url);
        conn.setAutoCommit(false);

        Statement stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS " + tableName + ";");
        context.getLogger().log("Dropped existing table: " + tableName + "\n");

        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        String headerLine = br.readLine();
        
        if (headerLine == null) {
            br.close();
            throw new Exception("CSV file is empty");
        }

        String[] headers = headerLine.split(",");
        context.getLogger().log("CSV headers: " + headerLine + "\n");
        context.getLogger().log("Number of columns: " + headers.length + "\n");

        // Create table with dynamic columns based on CSV header
        StringBuilder createTableSQL = new StringBuilder("CREATE TABLE " + tableName + " (");
        for (int i = 0; i < headers.length; i++) {
            String columnName = headers[i].trim().replace(" ", "_");
            createTableSQL.append("\"").append(columnName).append("\" TEXT");
            if (i < headers.length - 1) {
                createTableSQL.append(", ");
            }
        }
        createTableSQL.append(");");

        stmt.execute(createTableSQL.toString());
        context.getLogger().log("Created table with " + headers.length + " columns\n");

        // Prepare insert statement
        StringBuilder insertSQL = new StringBuilder("INSERT INTO " + tableName + " (");
        for (int i = 0; i < headers.length; i++) {
            String columnName = headers[i].trim().replace(" ", "_");
            insertSQL.append("\"").append(columnName).append("\"");
            if (i < headers.length - 1) {
                insertSQL.append(", ");
            }
        }
        insertSQL.append(") VALUES (");
        for (int i = 0; i < headers.length; i++) {
            insertSQL.append("?");
            if (i < headers.length - 1) {
                insertSQL.append(", ");
            }
        }
        insertSQL.append(");");

        PreparedStatement ps = conn.prepareStatement(insertSQL.toString());

        String line;
        int rowCount = 0;
        
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");

            for (int i = 0; i < headers.length; i++) {
                ps.setString(i + 1, (i < parts.length) ? parts[i].trim() : null);
            }

            ps.addBatch();
            rowCount++;

            // Log progress every 10000 rows
            if (rowCount % 10000 == 0) {
                context.getLogger().log("Processed " + rowCount + " rows\n");
            }
        }

        ps.executeBatch();
        context.getLogger().log("Total rows inserted: " + rowCount + "\n");
        
        conn.commit();
        conn.close();
        br.close();

        context.getLogger().log("Database creation completed successfully\n");
        return dbFile;
    }

    /** Upload file to S3 */
    private void uploadToS3(String bucket, String key, File file, Context context) throws IOException {
        AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        
        context.getLogger().log("Uploading database to S3: " + bucket + "/" + key + "\n");
        s3.putObject(bucket, key, file);
        context.getLogger().log("Successfully uploaded database to S3\n");
    }
}
