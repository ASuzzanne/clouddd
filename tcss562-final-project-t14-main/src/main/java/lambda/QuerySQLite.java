package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import java.io.*;
import java.sql.*;
import java.util.*;

public class QuerySQLite implements RequestHandler<HashMap<String, Object>, HashMap<String, Object>> {

    @Override
    public HashMap<String, Object> handleRequest(HashMap<String, Object> request, Context context) {
        HashMap<String, Object> response = new HashMap<>();

        try {
            String bucket = (String) request.get("bucket");
            String dbKey = (String) request.get("dbKey");
            String tableName = (String) request.get("tableName");
            String queryType = (String) request.getOrDefault("queryType", "select");
            Map<String, Object> queryParams = (Map<String, Object>) request.get("queryParams");

            context.getLogger().log("Starting query execution\n");
            context.getLogger().log("Database: s3://" + bucket + "/" + dbKey + "\n");

            // Get or download database
            File dbFile = getDatabaseFile(bucket, dbKey, context);
            
            // Execute query
            List<Map<String, Object>> results = executeQuery(dbFile, tableName, queryType, queryParams, context);

            response.put("status", "success");
            response.put("rowCount", results.size());
            response.put("results", results);
            response.put("message", "Query executed successfully.");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            context.getLogger().log("ERROR: " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        return response;
    }

    /** Get database file from /tmp cache or download from S3 */
    private File getDatabaseFile(String bucket, String key, Context context) throws IOException {
        String dbName = key.substring(key.lastIndexOf('/') + 1);
        File cachedDB = new File("/tmp/" + dbName);

        // Check if database exists in /tmp (warm Lambda)
        if (cachedDB.exists() && cachedDB.length() > 0) {
            context.getLogger().log("Using cached database from /tmp (warm infrastructure)\n");
            context.getLogger().log("Database size: " + cachedDB.length() + " bytes\n");
            return cachedDB;
        }

        // Download from S3 (cold Lambda)
        context.getLogger().log("Cache miss - downloading database from S3 (cold infrastructure)\n");
        AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        
        S3Object object = s3.getObject(bucket, key);
        InputStream input = object.getObjectContent();

        FileOutputStream fos = new FileOutputStream(cachedDB);
        byte[] buffer = new byte[4096];
        int length;
        long bytesRead = 0;

        while ((length = input.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
            bytesRead += length;
        }

        fos.close();
        input.close();

        context.getLogger().log("Downloaded " + bytesRead + " bytes to /tmp cache\n");
        return cachedDB;
    }

    /** Execute SQL query based on query type */
    private List<Map<String, Object>> executeQuery(File dbFile, String tableName, String queryType, 
                                                    Map<String, Object> queryParams, Context context) throws SQLException {
        String url = "jdbc:sqlite:file:" + dbFile.getAbsolutePath() + "?mode=ro";
        Connection conn = DriverManager.getConnection(url);
        
        context.getLogger().log("Connected to database in READ-ONLY mode\n");

        String sql = buildQuery(tableName, queryType, queryParams, context);
        context.getLogger().log("Executing SQL: " + sql + "\n");

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        // Get column metadata
        ResultSetMetaData metadata = rs.getMetaData();
        int columnCount = metadata.getColumnCount();

        // Build result list
        List<Map<String, Object>> results = new ArrayList<>();
        
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metadata.getColumnName(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            results.add(row);
        }

        rs.close();
        stmt.close();
        conn.close();

        context.getLogger().log("Query returned " + results.size() + " rows\n");
        return results;
    }

    /** Build SQL query based on type and parameters */
    private String buildQuery(String tableName, String queryType, Map<String, Object> params, Context context) {
        StringBuilder sql = new StringBuilder();

        switch (queryType.toLowerCase()) {
            case "count":
                sql.append("SELECT COUNT(*) as total FROM ").append(tableName);
                break;

            case "aggregate":
                String groupBy = (String) params.getOrDefault("groupBy", "Region");
                String aggFunction = (String) params.getOrDefault("function", "SUM");
                String aggColumn = (String) params.getOrDefault("column", "Total_Revenue");
                
                sql.append("SELECT \"").append(groupBy).append("\", ")
                   .append(aggFunction).append("(\"").append(aggColumn).append("\") as aggregate_value ")
                   .append("FROM ").append(tableName)
                   .append(" GROUP BY \"").append(groupBy).append("\"")
                   .append(" ORDER BY aggregate_value DESC");
                break;

            case "filter":
                String filterColumn = (String) params.getOrDefault("column", "Order_Priority");
                String filterValue = (String) params.getOrDefault("value", "H");
                
                sql.append("SELECT * FROM ").append(tableName)
                   .append(" WHERE \"").append(filterColumn).append("\" = '").append(filterValue).append("'")
                   .append(" LIMIT 100");
                break;

            case "top":
                int limit = (int) params.getOrDefault("limit", 10);
                String orderColumn = (String) params.getOrDefault("orderBy", "Total_Revenue");
                
                sql.append("SELECT * FROM ").append(tableName)
                   .append(" ORDER BY \"").append(orderColumn).append("\" DESC")
                   .append(" LIMIT ").append(limit);
                break;

            case "select":
            default:
                sql.append("SELECT * FROM ").append(tableName).append(" LIMIT 100");
                break;
        }

        return sql.toString();
    }
}
