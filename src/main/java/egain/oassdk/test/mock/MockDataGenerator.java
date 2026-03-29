package egain.oassdk.test.mock;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Generates mock data using OpenAPI specification.
 *
 * @deprecated Use {@link egain.oassdk.testgenerators.mock.MockDataGenerator} instead.
 *             This class is retained for backward compatibility with {@link egain.oassdk.test.TestSDK}.
 */
@Deprecated
public class MockDataGenerator {

    /**
     * Generate mock data
     *
     * @param spec      OpenAPI specification
     * @param outputDir Output directory
     * @throws GenerationException if generation fails
     */
    public void generateMockData(Map<String, Object> spec, String outputDir) throws GenerationException {
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Generate mock data for schemas
            generateSchemaMockData(spec, outputDir);

            // Generate mock data for requests
            generateRequestMockData(spec, outputDir);

            // Generate mock data for responses
            generateResponseMockData(spec, outputDir);

            // Generate mock data factory
            generateMockDataFactory(spec, outputDir);

            // Generate test data sets
            generateTestDataSets(spec, outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate mock data: " + e.getMessage(), e);
        }
    }

    /**
     * Generate mock data for schemas
     */
    private void generateSchemaMockData(Map<String, Object> spec, String outputDir) throws IOException {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null || !components.containsKey("schemas")) {
            return;
        }

        Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            // Generate mock data for this schema
            String mockData = generateMockDataForSchema(schemaName, schema);
            Files.write(Paths.get(outputDir, schemaName.toLowerCase() + "_mock.json"), mockData.getBytes());
        }
    }

    /**
     * Generate mock data for a specific schema
     */
    private String generateMockDataForSchema(String schemaName, Map<String, Object> schema) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        if (schema.containsKey("properties")) {
            Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
            boolean first = true;

            for (Map.Entry<String, Object> property : properties.entrySet()) {
                String fieldName = property.getKey();
                Map<String, Object> fieldSchema = Util.asStringObjectMap(property.getValue());

                if (!first) {
                    json.append(",\n");
                }

                json.append("  \"").append(fieldName).append("\": ");
                json.append(generateMockValue(fieldSchema));

                first = false;
            }
        }

        json.append("\n}");
        return json.toString();
    }

    /**
     * Generate mock value for a field
     */
    private String generateMockValue(Map<String, Object> fieldSchema) {
        String type = (String) fieldSchema.get("type");
        String format = (String) fieldSchema.get("format");

        switch (type) {
            case "string" -> {
                return switch (format) {
                    case "date" -> "\"2023-01-01\"";
                    case "date-time" -> "\"2023-01-01T00:00:00Z\"";
                    case "email" -> "\"user@example.com\"";
                    case "uuid" -> "\"550e8400-e29b-41d4-a716-446655440000\"";
                    case null, default -> "\"Sample " + type + " value\"";
                };
            }
            case "integer" -> {
                return "42";
            }
            case "number" -> {
                return "3.14";
            }
            case "boolean" -> {
                return "true";
            }
            case "array" -> {
                if (fieldSchema.containsKey("items")) {
                    Map<String, Object> items = Util.asStringObjectMap(fieldSchema.get("items"));
                    String itemValue = generateMockValue(items);
                    return "[" + itemValue + ", " + itemValue + "]";
                } else {
                    return "[]";
                }
            }
            case "object" -> {
                return "{}";
            }
            case null, default -> {
                return "null";
            }
        }
    }

    /**
     * Generate mock data for requests
     */
    private void generateRequestMockData(Map<String, Object> spec, String outputDir) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    generateRequestMockDataForOperation(path, method, operation, outputDir);
                }
            }
        }
    }

    /**
     * Generate mock data for a specific operation
     */
    private void generateRequestMockDataForOperation(String path, String method, Map<String, Object> operation, String outputDir) throws IOException {
        String operationId = (String) operation.get("operationId");
        String fileName = (operationId != null ? operationId : method + "_" + path.replaceAll("[^a-zA-Z0-9]", "_")) + "_request_mock.json";

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"method\": \"").append(method.toUpperCase()).append("\",\n");
        json.append("  \"path\": \"").append(path).append("\",\n");
        json.append("  \"headers\": {\n");
        json.append("    \"Content-Type\": \"application/json\",\n");
        json.append("    \"Accept\": \"application/json\"\n");
        json.append("  },\n");
        json.append("  \"body\": ");

        if (operation.containsKey("requestBody")) {
            Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
            if (requestBody.containsKey("content")) {
                Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));
                if (content != null && content.containsKey("application/json")) {
                    Map<String, Object> jsonContent = Util.asStringObjectMap(content.get("application/json"));
                    if (jsonContent != null && jsonContent.containsKey("schema")) {
                        Map<String, Object> schema = Util.asStringObjectMap(jsonContent.get("schema"));
                        json.append(generateMockDataForSchema("request", schema));
                    } else {
                        json.append("{}");
                    }
                } else {
                    json.append("{}");
                }
            } else {
                json.append("{}");
            }
        } else {
            json.append("{}");
        }

        json.append("\n}");

        Files.write(Paths.get(outputDir, fileName), json.toString().getBytes());
    }

    /**
     * Generate mock data for responses
     */
    private void generateResponseMockData(Map<String, Object> spec, String outputDir) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    generateResponseMockDataForOperation(path, method, operation, outputDir);
                }
            }
        }
    }

    /**
     * Generate mock data for a specific operation response
     */
    private void generateResponseMockDataForOperation(String path, String method, Map<String, Object> operation, String outputDir) throws IOException {
        String operationId = (String) operation.get("operationId");
        String fileName = (operationId != null ? operationId : method + "_" + path.replaceAll("[^a-zA-Z0-9]", "_")) + "_response_mock.json";

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"method\": \"").append(method.toUpperCase()).append("\",\n");
        json.append("  \"path\": \"").append(path).append("\",\n");
        json.append("  \"responses\": {\n");

        if (operation.containsKey("responses")) {
            Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
            boolean first = true;

            for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                String statusCode = responseEntry.getKey();
                Map<String, Object> response = Util.asStringObjectMap(responseEntry.getValue());

                if (!first) {
                    json.append(",\n");
                }

                json.append("    \"").append(statusCode).append("\": {\n");
                json.append("      \"status\": ").append(statusCode).append(",\n");
                json.append("      \"headers\": {\n");
                json.append("        \"Content-Type\": \"application/json\"\n");
                json.append("      },\n");
                json.append("      \"body\": ");

                if (response.containsKey("content")) {
                    Map<String, Object> content = Util.asStringObjectMap(response.get("content"));
                    if (content != null && content.containsKey("application/json")) {
                        Map<String, Object> jsonContent = Util.asStringObjectMap(content.get("application/json"));
                        if (jsonContent != null && jsonContent.containsKey("schema")) {
                            Map<String, Object> schema = Util.asStringObjectMap(jsonContent.get("schema"));
                            json.append(generateMockDataForSchema("response", schema));
                        } else {
                            json.append("{}");
                        }
                    } else {
                        json.append("{}");
                    }
                } else {
                    json.append("{}");
                }

                json.append("\n    }");
                first = false;
            }
        }

        json.append("\n  }\n}");

        Files.write(Paths.get(outputDir, fileName), json.toString().getBytes());
    }

    /**
     * Generate mock data factory
     */
    private void generateMockDataFactory(Map<String, Object> spec, String outputDir) throws IOException {
        String factoryContent = """
                package com.example.test.mock;
                
                import com.fasterxml.jackson.databind.ObjectMapper;
                import java.util.Map;
                import java.util.HashMap;
                import java.util.List;
                import java.util.ArrayList;
                import java.util.Random;
                
                public class MockDataFactory {
                
                    private final ObjectMapper objectMapper = new ObjectMapper();
                    private final Random random = new Random();
                
                    /**
                     * Generate mock data for a schema
                     */
                    public Map<String, Object> generateMockData(String schemaName) {
                        Map<String, Object> mockData = new HashMap<>();
                
                        switch (schemaName.toLowerCase()) {
                            case "user":
                                return generateUserMockData();
                            case "product":
                                return generateProductMockData();
                            case "order":
                                return generateOrderMockData();
                            default:
                                return generateGenericMockData();
                        }
                    }
                
                    private Map<String, Object> generateUserMockData() {
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", random.nextInt(1000));
                        user.put("name", "John Doe");
                        user.put("email", "john.doe@example.com");
                        user.put("age", 25 + random.nextInt(50));
                        user.put("active", true);
                        return user;
                    }
                
                    private Map<String, Object> generateProductMockData() {
                        Map<String, Object> product = new HashMap<>();
                        product.put("id", random.nextInt(1000));
                        product.put("name", "Sample Product");
                        product.put("description", "A sample product description");
                        product.put("price", 10.0 + random.nextDouble() * 100);
                        product.put("category", "Electronics");
                        product.put("inStock", random.nextBoolean());
                        return product;
                    }
                
                    private Map<String, Object> generateOrderMockData() {
                        Map<String, Object> order = new HashMap<>();
                        order.put("id", random.nextInt(1000));
                        order.put("userId", random.nextInt(1000));
                        order.put("total", 50.0 + random.nextDouble() * 200);
                        order.put("status", "pending");
                        order.put("items", generateOrderItems());
                        return order;
                    }
                
                    private List<Map<String, Object>> generateOrderItems() {
                        List<Map<String, Object>> items = new ArrayList<>();
                        int itemCount = 1 + random.nextInt(5);
                
                        for (int i = 0; i < itemCount; i++) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("productId", random.nextInt(1000));
                            item.put("quantity", 1 + random.nextInt(10));
                            item.put("price", 10.0 + random.nextDouble() * 50);
                            items.add(item);
                        }
                
                        return items;
                    }
                
                    private Map<String, Object> generateGenericMockData() {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", random.nextInt(1000));
                        data.put("name", "Sample Data");
                        data.put("value", random.nextDouble());
                        data.put("active", true);
                        return data;
                    }
                
                    /**
                     * Generate multiple mock data entries
                     */
                    public List<Map<String, Object>> generateMockDataList(String schemaName, int count) {
                        List<Map<String, Object>> dataList = new ArrayList<>();
                
                        for (int i = 0; i < count; i++) {
                            dataList.add(generateMockData(schemaName));
                        }
                
                        return dataList;
                    }
                
                    /**
                     * Generate mock data with specific constraints
                     */
                    public Map<String, Object> generateMockDataWithConstraints(String schemaName, Map<String, Object> constraints) {
                        Map<String, Object> mockData = generateMockData(schemaName);
                
                        // Apply constraints
                        constraints.forEach((key, value) -> {
                            if (mockData.containsKey(key)) {
                                mockData.put(key, value);
                            }
                        });
                
                        return mockData;
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "MockDataFactory.java"), factoryContent.getBytes());
    }

    /**
     * Generate test data sets
     */
    private void generateTestDataSets(Map<String, Object> spec, String outputDir) throws IOException {
        // Generate positive test data
        String positiveData = """
                {
                  "positive_test_data": {
                    "valid_requests": [
                      {
                        "name": "Valid User Creation",
                        "data": {
                          "name": "John Doe",
                          "email": "john.doe@example.com",
                          "age": 30
                        }
                      },
                      {
                        "name": "Valid Product Creation",
                        "data": {
                          "name": "Sample Product",
                          "price": 99.99,
                          "category": "Electronics"
                        }
                      }
                    ],
                    "valid_responses": [
                      {
                        "name": "Success Response",
                        "status": 200,
                        "data": {
                          "success": true,
                          "message": "Operation completed successfully"
                        }
                      }
                    ]
                  }
                }
                """;

        Files.write(Paths.get(outputDir, "positive_test_data.json"), positiveData.getBytes());

        // Generate negative test data
        String negativeData = """
                {
                  "negative_test_data": {
                    "invalid_requests": [
                      {
                        "name": "Invalid Email Format",
                        "data": {
                          "name": "John Doe",
                          "email": "invalid-email",
                          "age": 30
                        }
                      },
                      {
                        "name": "Missing Required Field",
                        "data": {
                          "email": "john.doe@example.com",
                          "age": 30
                        }
                      },
                      {
                        "name": "Invalid Age Range",
                        "data": {
                          "name": "John Doe",
                          "email": "john.doe@example.com",
                          "age": -5
                        }
                      }
                    ],
                    "error_responses": [
                      {
                        "name": "Validation Error",
                        "status": 400,
                        "data": {
                          "error": "Validation failed",
                          "details": "Invalid input data"
                        }
                      },
                      {
                        "name": "Not Found Error",
                        "status": 404,
                        "data": {
                          "error": "Resource not found",
                          "details": "The requested resource does not exist"
                        }
                      }
                    ]
                  }
                }
                """;

        Files.write(Paths.get(outputDir, "negative_test_data.json"), negativeData.getBytes());

        // Generate edge case test data
        String edgeCaseData = """
                {
                  "edge_case_test_data": {
                    "boundary_values": [
                      {
                        "name": "Minimum String Length",
                        "data": {
                          "name": "A",
                          "email": "a@b.co",
                          "age": 0
                        }
                      },
                      {
                        "name": "Maximum String Length",
                        "data": {
                          "name": "A".repeat(100),
                          "email": "very.long.email.address@example.com",
                          "age": 150
                        }
                      }
                    ],
                    "special_characters": [
                      {
                        "name": "Unicode Characters",
                        "data": {
                          "name": "José María",
                          "email": "josé.maría@example.com",
                          "age": 30
                        }
                      },
                      {
                        "name": "Special Symbols",
                        "data": {
                          "name": "John O'Connor",
                          "email": "john.o'connor@example.com",
                          "age": 30
                        }
                      }
                    ]
                  }
                }
                """;

        Files.write(Paths.get(outputDir, "edge_case_test_data.json"), edgeCaseData.getBytes());
    }
}
