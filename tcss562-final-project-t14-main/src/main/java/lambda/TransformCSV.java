package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class TransformCSV implements RequestHandler<HashMap<String, Object>, HashMap<String, Object>> {

    @Override
    public HashMap<String, Object> handleRequest(HashMap<String, Object> request, Context context) {
        HashMap<String, Object> response = new HashMap<>();

        try {
            String sourceBucket = (String) request.get("sourceBucket");
            String sourceKey = (String) request.get("sourceKey");
            String destBucket = (String) request.get("destBucket");
            String destKey = (String) request.getOrDefault("destKey", "transformed-" + sourceKey);

            context.getLogger().log("Starting CSV transformation\n");
            context.getLogger().log("Source: s3://" + sourceBucket + "/" + sourceKey + "\n");

            // Download CSV from S3
            File csvFile = downloadFromS3(sourceBucket, sourceKey, context);
            
            // Transform CSV
            File transformedFile = transformCSV(csvFile, context);
            
            // Upload transformed CSV back to S3
            uploadToS3(destBucket, destKey, transformedFile, context);

            response.put("status", "success");
            response.put("sourceLocation", "s3://" + sourceBucket + "/" + sourceKey);
            response.put("destLocation", "s3://" + destBucket + "/" + destKey);
            response.put("message", "CSV transformed successfully.");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            context.getLogger().log("ERROR: " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        return response;
    }

    /** Download file from S3 */
    private File downloadFromS3(String bucket, String key, Context context) throws IOException {
        AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();

        context.getLogger().log("Downloading CSV from S3\n");
        S3Object object = s3.getObject(bucket, key);
        InputStream input = object.getObjectContent();

        File temp = File.createTempFile("input", ".csv");
        FileOutputStream fos = new FileOutputStream(temp);

        byte[] buffer = new byte[4096];
        int length;

        while ((length = input.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
        }

        fos.close();
        input.close();
        
        context.getLogger().log("Downloaded " + temp.length() + " bytes\n");
        return temp;
    }

    /** Transform CSV: remove duplicates, sort, add processing time */
    private File transformCSV(File inputFile, Context context) throws IOException, ParseException {
        context.getLogger().log("Starting CSV transformation\n");

        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        String headerLine = reader.readLine();
        
        if (headerLine == null) {
            reader.close();
            throw new IOException("CSV file is empty");
        }

        String[] headers = headerLine.split(",");
        context.getLogger().log("Headers: " + headerLine + "\n");

        // Find column indices
        int orderIdIndex = findColumnIndex(headers, "Order ID");
        int orderPriorityIndex = findColumnIndex(headers, "Order Priority");
        int orderDateIndex = findColumnIndex(headers, "Order Date");
        int shipDateIndex = findColumnIndex(headers, "Ship Date");

        // Read all records and remove duplicates by Order ID
        Map<String, String[]> uniqueRecords = new LinkedHashMap<>();
        String line;
        int rowCount = 0;

        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length > orderIdIndex) {
                String orderId = parts[orderIdIndex].trim();
                if (!uniqueRecords.containsKey(orderId)) {
                    uniqueRecords.put(orderId, parts);
                }
            }
            rowCount++;
        }
        reader.close();

        context.getLogger().log("Original rows: " + rowCount + "\n");
        context.getLogger().log("Unique rows: " + uniqueRecords.size() + "\n");
        context.getLogger().log("Duplicates removed: " + (rowCount - uniqueRecords.size()) + "\n");

        // Sort by Order Priority (L=Low, M=Medium, H=High, C=Critical)
        List<String[]> sortedRecords = new ArrayList<>(uniqueRecords.values());
        sortedRecords.sort((a, b) -> {
            String priorityA = a[orderPriorityIndex].trim();
            String priorityB = b[orderPriorityIndex].trim();
            return getPriorityOrder(priorityA) - getPriorityOrder(priorityB);
        });

        context.getLogger().log("Records sorted by priority\n");

        // Write transformed CSV with new column
        File outputFile = File.createTempFile("transformed", ".csv");
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

        // Write header with new column
        writer.write(headerLine + ",Order Processing Time (days)\n");

        // Write sorted and transformed records
        SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yyyy");
        
        for (String[] record : sortedRecords) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < record.length; i++) {
                sb.append(record[i]);
                if (i < record.length - 1) {
                    sb.append(",");
                }
            }

            // Calculate processing time
            try {
                String orderDateStr = record[orderDateIndex].trim();
                String shipDateStr = record[shipDateIndex].trim();
                
                Date orderDate = dateFormat.parse(orderDateStr);
                Date shipDate = dateFormat.parse(shipDateStr);
                
                long diffMillis = shipDate.getTime() - orderDate.getTime();
                long diffDays = diffMillis / (1000 * 60 * 60 * 24);
                
                sb.append(",").append(diffDays);
            } catch (Exception e) {
                sb.append(",0");
            }

            sb.append("\n");
            writer.write(sb.toString());
        }

        writer.close();
        context.getLogger().log("Transformation complete. Output size: " + outputFile.length() + " bytes\n");

        return outputFile;
    }

    /** Find column index by name */
    private int findColumnIndex(String[] headers, String columnName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    /** Get priority order for sorting */
    private int getPriorityOrder(String priority) {
        switch (priority.toUpperCase()) {
            case "L": return 1;  // Low
            case "M": return 2;  // Medium
            case "H": return 3;  // High
            case "C": return 4;  // Critical
            default: return 0;
        }
    }

    /** Upload file to S3 */
    private void uploadToS3(String bucket, String key, File file, Context context) throws IOException {
        AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        
        context.getLogger().log("Uploading transformed CSV to S3: " + bucket + "/" + key + "\n");
        s3.putObject(bucket, key, file);
        context.getLogger().log("Successfully uploaded to S3\n");
    }
}
