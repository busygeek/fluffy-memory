// pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>
    <groupId>com.mockapi</groupId>
    <artifactId>mock-rest-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>mock-rest-api</name>
    <description>Mock REST API with custom responses</description>
    <properties>
        <java.version>21</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>

// Main Application Class
// src/main/java/com/mockapi/MockRestApiApplication.java
package com.mockapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MockRestApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockRestApiApplication.class, args);
    }
}

// Generic Response Model
// src/main/java/com/mockapi/model/ApiResponse.java
package com.mockapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse(
    int status,
    String message,
    Object data,
    LocalDateTime timestamp,
    String path,
    Map<String, Object> metadata
) {
    public static ApiResponse success(Object data) {
        return new ApiResponse(200, "Success", data, LocalDateTime.now(), null, null);
    }
    
    public static ApiResponse success(Object data, String message) {
        return new ApiResponse(200, message, data, LocalDateTime.now(), null, null);
    }
    
    public static ApiResponse error(int status, String message, String path) {
        return new ApiResponse(status, message, null, LocalDateTime.now(), path, null);
    }
    
    public static ApiResponse custom(int status, String message, Object data, Map<String, Object> metadata) {
        return new ApiResponse(status, message, data, LocalDateTime.now(), null, metadata);
    }
}

// Custom Exception for Mock API
// src/main/java/com/mockapi/exception/MockApiException.java
package com.mockapi.exception;

public class MockApiException extends RuntimeException {
    private final int statusCode;
    
    public MockApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
}

// File Storage Service
// src/main/java/com/mockapi/service/FileStorageService.java
package com.mockapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockapi.exception.MockApiException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class FileStorageService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String dataDirectory = "mock-data";
    
    public FileStorageService() {
        createDataDirectory();
    }
    
    private void createDataDirectory() {
        try {
            Path path = Paths.get(dataDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new MockApiException(500, "Failed to create data directory: " + e.getMessage());
        }
    }
    
    public <T> List<T> readFromFile(String filename, TypeReference<List<T>> typeRef) {
        File file = new File(dataDirectory, filename);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(file, typeRef);
        } catch (IOException e) {
            throw new MockApiException(500, "Failed to read from file: " + e.getMessage());
        }
    }
    
    public <T> void writeToFile(String filename, List<T> data) {
        File file = new File(dataDirectory, filename);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            throw new MockApiException(500, "Failed to write to file: " + e.getMessage());
        }
    }
    
    public <T> T readSingleFromFile(String filename, Class<T> clazz) {
        File file = new File(dataDirectory, filename);
        if (!file.exists()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(file, clazz);
        } catch (IOException e) {
            throw new MockApiException(500, "Failed to read from file: " + e.getMessage());
        }
    }
    
    public <T> void writeSingleToFile(String filename, T data) {
        File file = new File(dataDirectory, filename);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            throw new MockApiException(500, "Failed to write to file: " + e.getMessage());
        }
    }
    
    public boolean deleteFile(String filename) {
        File file = new File(dataDirectory, filename);
        return file.delete();
    }
    
    public List<String> listFiles() {
        File dir = new File(dataDirectory);
        String[] files = dir.list();
        return files != null ? Arrays.asList(files) : new ArrayList<>();
    }
}

// Generic CRUD Controller
// src/main/java/com/mockapi/controller/GenericCrudController.java
package com.mockapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.ApiResponse;
import com.mockapi.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/data")
public class GenericCrudController {
    
    @Autowired
    private FileStorageService fileStorage;
    
    // Get all items from a collection
    @GetMapping("/{collection}")
    public ResponseEntity<ApiResponse> getAllItems(@PathVariable String collection) {
        String filename = collection + ".json";
        List<Map<String, Object>> items = fileStorage.readFromFile(filename, 
            new TypeReference<List<Map<String, Object>>>() {});
        return ResponseEntity.ok(ApiResponse.success(items, "Retrieved " + items.size() + " items"));
    }
    
    // Get single item by ID
    @GetMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> getItem(@PathVariable String collection, @PathVariable String id) {
        String filename = collection + ".json";
        List<Map<String, Object>> items = fileStorage.readFromFile(filename, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        Optional<Map<String, Object>> item = items.stream()
            .filter(i -> id.equals(String.valueOf(i.get("id"))))
            .findFirst();
            
        if (item.isEmpty()) {
            throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
        }
        
        return ResponseEntity.ok(ApiResponse.success(item.get()));
    }
    
    // Create new item
    @PostMapping("/{collection}")
    public ResponseEntity<ApiResponse> createItem(@PathVariable String collection, 
                                                 @RequestBody Map<String, Object> item) {
        String filename = collection + ".json";
        List<Map<String, Object>> items = fileStorage.readFromFile(filename, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        // Generate ID if not provided
        if (!item.containsKey("id")) {
            String newId = UUID.randomUUID().toString();
            item.put("id", newId);
        }
        
        item.put("createdAt", new Date());
        items.add(item);
        fileStorage.writeToFile(filename, items);
        
        return ResponseEntity.status(201).body(ApiResponse.custom(201, "Item created successfully", item, null));
    }
    
    // Update item
    @PutMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> updateItem(@PathVariable String collection, 
                                                 @PathVariable String id,
                                                 @RequestBody Map<String, Object> updatedItem) {
        String filename = collection + ".json";
        List<Map<String, Object>> items = fileStorage.readFromFile(filename, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            if (id.equals(String.valueOf(item.get("id")))) {
                updatedItem.put("id", id);
                updatedItem.put("updatedAt", new Date());
                items.set(i, updatedItem);
                fileStorage.writeToFile(filename, items);
                return ResponseEntity.ok(ApiResponse.success(updatedItem, "Item updated successfully"));
            }
        }
        
        throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
    }
    
    // Delete item
    @DeleteMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> deleteItem(@PathVariable String collection, @PathVariable String id) {
        String filename = collection + ".json";
        List<Map<String, Object>> items = fileStorage.readFromFile(filename, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        boolean removed = items.removeIf(item -> id.equals(String.valueOf(item.get("id"))));
        
        if (!removed) {
            throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
        }
        
        fileStorage.writeToFile(filename, items);
        return ResponseEntity.ok(ApiResponse.success(null, "Item deleted successfully"));
    }
}

// Error Simulation Controller
// src/main/java/com/mockapi/controller/ErrorController.java
package com.mockapi.controller;

import com.mockapi.exception.MockApiException;
import com.mockapi.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/errors")
public class ErrorController {
    
    @GetMapping("/200")
    public ResponseEntity<ApiResponse> ok() {
        return ResponseEntity.ok(ApiResponse.success("Everything is OK!", "Success response"));
    }
    
    @GetMapping("/201")
    public ResponseEntity<ApiResponse> created() {
        return ResponseEntity.status(201)
            .body(ApiResponse.custom(201, "Resource created", Map.of("id", "123"), null));
    }
    
    @GetMapping("/400")
    public ResponseEntity<ApiResponse> badRequest() {
        throw new MockApiException(400, "Bad Request - Invalid input provided");
    }
    
    @GetMapping("/401")
    public ResponseEntity<ApiResponse> unauthorized() {
        throw new MockApiException(401, "Unauthorized - Authentication required");
    }
    
    @GetMapping("/403")
    public ResponseEntity<ApiResponse> forbidden() {
        throw new MockApiException(403, "Forbidden - Access denied");
    }
    
    @GetMapping("/404")
    public ResponseEntity<ApiResponse> notFound() {
        throw new MockApiException(404, "Not Found - Resource does not exist");
    }
    
    @GetMapping("/500")
    public ResponseEntity<ApiResponse> internalServerError() {
        throw new MockApiException(500, "Internal Server Error - Something went wrong");
    }
    
    @GetMapping("/502")
    public ResponseEntity<ApiResponse> badGateway() {
        throw new MockApiException(502, "Bad Gateway - Upstream server error");
    }
    
    @GetMapping("/503")
    public ResponseEntity<ApiResponse> serviceUnavailable() {
        throw new MockApiException(503, "Service Unavailable - Server is temporarily unavailable");
    }
    
    // Custom error with any status code
    @GetMapping("/{statusCode}")
    public ResponseEntity<ApiResponse> customError(@PathVariable int statusCode,
                                                  @RequestParam(defaultValue = "Custom error message") String message) {
        throw new MockApiException(statusCode, message);
    }
}

// Custom Response Controller
// src/main/java/com/mockapi/controller/CustomResponseController.java
package com.mockapi.controller;

import com.mockapi.model.ApiResponse;
import com.mockapi.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/custom")
public class CustomResponseController {
    
    @Autowired
    private FileStorageService fileStorage;
    
    // Store a custom response that can be retrieved later
    @PostMapping("/response/{key}")
    public ResponseEntity<ApiResponse> storeCustomResponse(@PathVariable String key,
                                                          @RequestBody Map<String, Object> response,
                                                          @RequestParam(defaultValue = "200") int status) {
        String filename = "custom_response_" + key + ".json";
        Map<String, Object> customResponse = Map.of(
            "status", status,
            "response", response,
            "key", key
        );
        fileStorage.writeSingleToFile(filename, customResponse);
        
        return ResponseEntity.ok(ApiResponse.success(customResponse, "Custom response stored for key: " + key));
    }
    
    // Retrieve and return the stored custom response
    @GetMapping("/response/{key}")
    public ResponseEntity<Object> getCustomResponse(@PathVariable String key) {
        String filename = "custom_response_" + key + ".json";
        @SuppressWarnings("unchecked")
        Map<String, Object> storedResponse = fileStorage.readSingleFromFile(filename, Map.class);
        
        if (storedResponse == null) {
            return ResponseEntity.notFound().build();
        }
        
        int status = (Integer) storedResponse.get("status");
        Object response = storedResponse.get("response");
        
        return ResponseEntity.status(status).body(response);
    }
    
    // Echo endpoint - returns exactly what you send
    @PostMapping("/echo")
    public ResponseEntity<Object> echo(@RequestBody Object body,
                                      @RequestParam(defaultValue = "200") int status) {
        return ResponseEntity.status(status).body(body);
    }
    
    // Dynamic response based on request parameters
    @GetMapping("/dynamic")
    public ResponseEntity<ApiResponse> dynamicResponse(@RequestParam(defaultValue = "200") int status,
                                                      @RequestParam(defaultValue = "Dynamic response") String message,
                                                      @RequestParam(required = false) String data) {
        Object responseData = data != null ? Map.of("customData", data) : null;
        return ResponseEntity.status(status)
            .body(ApiResponse.custom(status, message, responseData, null));
    }
}

// Global Exception Handler
// src/main/java/com/mockapi/exception/GlobalExceptionHandler.java
package com.mockapi.exception;

import com.mockapi.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MockApiException.class)
    public ResponseEntity<ApiResponse> handleMockApiException(MockApiException ex, WebRequest request) {
        ApiResponse response = ApiResponse.error(ex.getStatusCode(), ex.getMessage(), 
            request.getDescription(false));
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception ex, WebRequest request) {
        ApiResponse response = ApiResponse.error(500, "Internal server error: " + ex.getMessage(), 
            request.getDescription(false));
        return ResponseEntity.status(500).body(response);
    }
}

// Application Properties
// src/main/resources/application.yml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: mock-rest-api
  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null

logging:
  level:
    com.mockapi: INFO
    org.springframework.web: DEBUG