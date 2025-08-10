// Generic CRUD Controller
// src/main/java/com/mockapi/controller/GenericCrudController.java
package com.mockapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.ApiResponse;
import com.mockapi.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.*;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Slf4j
public class GenericCrudController {
    
    private final FileStorageService fileStorage;
    
    @PostConstruct
    public void init() {
        fileStorage.createDataDirectory();
    }
    
    // Get all collections
    @GetMapping("/collections")
    public ResponseEntity<ApiResponse> getAllCollections() {
        List<String> collections = fileStorage.listCollections();
        Map<String, Object> result = Map.of(
            "collections", collections,
            "count", collections.size()
        );
        log.info("Retrieved {} collections", collections.size());
        return ResponseEntity.ok(ApiResponse.success(result, "Retrieved " + collections.size() + " collections"));
    }
    
    // Get all items from a collection (stored as single data.json file)
    @GetMapping("/{collection}")
    public ResponseEntity<ApiResponse> getAllItems(@PathVariable String collection) {
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        Map<String, Object> result = Map.of(
            "collection", collection,
            "items", items,
            "count", items.size()
        );
        log.info("Retrieved {} items from collection: {}", items.size(), collection);
        return ResponseEntity.ok(ApiResponse.success(result, "Retrieved " + items.size() + " items from " + collection));
    }
    
    // Get single item by ID (can be stored as individual file or from collection data)
    @GetMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> getItem(@PathVariable String collection, @PathVariable String id) {
        // First try to get individual item file
        @SuppressWarnings("unchecked")
        Map<String, Object> item = fileStorage.readItemFromCollection(collection, id, Map.class);
        
        if (item != null) {
            log.info("Retrieved item {} from individual file in collection: {}", id, collection);
            return ResponseEntity.ok(ApiResponse.success(item));
        }
        
        // Fallback to collection data.json
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        Optional<Map<String, Object>> foundItem = items.stream()
            .filter(i -> id.equals(String.valueOf(i.get("id"))))
            .findFirst();
            
        if (foundItem.isEmpty()) {
            log.warn("Item with id {} not found in collection: {}", id, collection);
            throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
        }
        
        log.info("Retrieved item {} from collection data file: {}", id, collection);
        return ResponseEntity.ok(ApiResponse.success(foundItem.get()));
    }
    
    // Create new item
    @PostMapping("/{collection}")
    public ResponseEntity<ApiResponse> createItem(@PathVariable String collection, 
                                                 @RequestBody Map<String, Object> item,
                                                 @RequestParam(defaultValue = "false") boolean individualFile) {
        // Generate ID if not provided
        if (!item.containsKey("id")) {
            String newId = UUID.randomUUID().toString();
            item.put("id", newId);
        }
        
        String itemId = String.valueOf(item.get("id"));
        item.put("createdAt", new Date());
        
        if (individualFile) {
            // Store as individual file
            fileStorage.writeItemToCollection(collection, itemId, item);
            log.info("Created item {} as individual file in collection: {}", itemId, collection);
        } else {
            // Store in collection data.json
            List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                new TypeReference<List<Map<String, Object>>>() {});
            items.add(item);
            fileStorage.writeCollectionData(collection, items);
            log.info("Created item {} in collection data file: {}", itemId, collection);
        }
        
        return ResponseEntity.status(201).body(ApiResponse.custom(201, "Item created successfully in " + collection, item, 
            Map.of("storage", individualFile ? "individual_file" : "collection_file")));
    }
    
    // Update item
    @PutMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> updateItem(@PathVariable String collection, 
                                                 @PathVariable String id,
                                                 @RequestBody Map<String, Object> updatedItem) {
        updatedItem.put("id", id);
        updatedItem.put("updatedAt", new Date());
        
        // Try individual file first
        @SuppressWarnings("unchecked")
        Map<String, Object> existingItem = fileStorage.readItemFromCollection(collection, id, Map.class);
        
        if (existingItem != null) {
            fileStorage.writeItemToCollection(collection, id, updatedItem);
            log.info("Updated item {} as individual file in collection: {}", id, collection);
            return ResponseEntity.ok(ApiResponse.success(updatedItem, "Item updated successfully in " + collection));
        }
        
        // Fallback to collection data.json
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            if (id.equals(String.valueOf(item.get("id")))) {
                items.set(i, updatedItem);
                fileStorage.writeCollectionData(collection, items);
                log.info("Updated item {} in collection data file: {}", id, collection);
                return ResponseEntity.ok(ApiResponse.success(updatedItem, "Item updated successfully in " + collection));
            }
        }
        
        log.warn("Attempted to update non-existent item {} in collection: {}", id, collection);
        throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
    }
    
    // Delete item
    @DeleteMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> deleteItem(@PathVariable String collection, @PathVariable String id) {
        // Try individual file first
        boolean deletedFromFile = fileStorage.deleteItemFromCollection(collection, id);
        
        if (deletedFromFile) {
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
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
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

// Configuration Class for ObjectMapper and other beans
// src/main/java/com/mockapi/config/AppConfig.java
package com.mockapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {
    
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        return mapper;
    }
}

// Generic Response Model
// src/main/java/com/mockapi/model/ApiResponse.java
package com.mockapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.With;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
@With
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse(
    int status,
    String message,
    Object data,
    @Builder.Default LocalDateTime timestamp,
    String path,
    Map<String, Object> metadata
) {
    public static ApiResponse success(Object data) {
        return ApiResponse.builder()
                .status(200)
                .message("Success")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static ApiResponse success(Object data, String message) {
        return ApiResponse.builder()
                .status(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static ApiResponse error(int status, String message, String path) {
        return ApiResponse.builder()
                .status(status)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
    
    public static ApiResponse custom(int status, String message, Object data, Map<String, Object> metadata) {
        return ApiResponse.builder()
                .status(status)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .metadata(metadata)
                .build();
    }
}

// Custom Exception for Mock API
// src/main/java/com/mockapi/exception/MockApiException.java
package com.mockapi.exception;

import lombok.Getter;

@Getter
public class MockApiException extends RuntimeException {
    private final int statusCode;
    
    public MockApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}

// File Storage Service
// src/main/java/com/mockapi/service/FileStorageService.java
package com.mockapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockapi.exception.MockApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {
    
    private final ObjectMapper objectMapper;
    private final String dataDirectory = "mock-data";
    
    public void createDataDirectory() {
        try {
            Path path = Paths.get(dataDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created data directory: {}", dataDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to create data directory: {}", e.getMessage());
            throw new MockApiException(500, "Failed to create data directory: " + e.getMessage());
        }
    }
    
    private void createCollectionDirectory(String collection) {
        try {
            Path collectionPath = Paths.get(dataDirectory, collection);
            if (!Files.exists(collectionPath)) {
                Files.createDirectories(collectionPath);
                log.info("Created collection directory: {}", collectionPath);
            }
        } catch (IOException e) {
            log.error("Failed to create collection directory: {}", e.getMessage());
            throw new MockApiException(500, "Failed to create collection directory: " + e.getMessage());
        }
    }
    
    // Collection-based methods (each collection has its own directory)
    public <T> List<T> readCollectionData(String collection, TypeReference<List<T>> typeRef) {
        createCollectionDirectory(collection);
        File file = new File(dataDirectory + File.separator + collection, "data.json");
        if (!file.exists()) {
            log.debug("Collection data file not found: {}", file.getPath());
            return new ArrayList<>();
        }
        
        try {
            List<T> data = objectMapper.readValue(file, typeRef);
            log.debug("Read {} items from collection: {}", data.size(), collection);
            return data;
        } catch (IOException e) {
            log.error("Failed to read collection data from {}: {}", file.getPath(), e.getMessage());
            throw new MockApiException(500, "Failed to read collection data: " + e.getMessage());
        }
    }
    
    public <T> void writeCollectionData(String collection, List<T> data) {
        createCollectionDirectory(collection);
        File file = new File(dataDirectory + File.separator + collection, "data.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            log.debug("Wrote {} items to collection: {}", data.size(), collection);
        } catch (IOException e) {
            log.error("Failed to write collection data to {}: {}", file.getPath(), e.getMessage());
            throw new MockApiException(500, "Failed to write collection data: " + e.getMessage());
        }
    }
    
    // Individual item methods (store each item as separate file in collection directory)
    public <T> T readItemFromCollection(String collection, String itemId, Class<T> clazz) {
        createCollectionDirectory(collection);
        File file = new File(dataDirectory + File.separator + collection, itemId + ".json");
        if (!file.exists()) {
            log.debug("Item file not found: {}", file.getPath());
            return null;
        }
        
        try {
            T item = objectMapper.readValue(file, clazz);
            log.debug("Read item {} from collection: {}", itemId, collection);
            return item;
        } catch (IOException e) {
            log.error("Failed to read item from {}: {}", file.getPath(), e.getMessage());
            throw new MockApiException(500, "Failed to read item from collection: " + e.getMessage());
        }
    }
    
    public <T> void writeItemToCollection(String collection, String itemId, T data) {
        createCollectionDirectory(collection);
        File file = new File(dataDirectory + File.separator + collection, itemId + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            log.debug("Wrote item {} to collection: {}", itemId, collection);
        } catch (IOException e) {
            log.error("Failed to write item to {}: {}", file.getPath(), e.getMessage());
            throw new MockApiException(500, "Failed to write item to collection: " + e.getMessage());
        }
    }
    
    public boolean deleteItemFromCollection(String collection, String itemId) {
        File file = new File(dataDirectory + File.separator + collection, itemId + ".json");
        boolean deleted = file.delete();
        if (deleted) {
            log.debug("Deleted item {} from collection: {}", itemId, collection);
        } else {
            log.debug("Failed to delete item {} from collection: {}", itemId, collection);
        }
        return deleted;
    }
    
    public List<String> getItemIdsFromCollection(String collection) {
        createCollectionDirectory(collection);
        File collectionDir = new File(dataDirectory, collection);
        String[] files = collectionDir.list((dir, name) -> name.endsWith(".json") && !name.equals("data.json"));
        if (files == null) {
            return new ArrayList<>();
        }
        
        List<String> itemIds = Arrays.stream(files)
                .map(filename -> filename.substring(0, filename.lastIndexOf(".json")))
                .toList();
        
        log.debug("Found {} individual items in collection: {}", itemIds.size(), collection);
        return itemIds;
    }
    
    // Legacy methods for backward compatibility and custom responses
    public <T> List<T> readFromFile(String filename, TypeReference<List<T>> typeRef) {
        File file = new File(dataDirectory, filename);
        if (!file.exists()) {
            log.debug("File not found: {}", file.getPath());
            return new ArrayList<>();
        }
        
        try {
            List<T> data = objectMapper.readValue(file, typeRef);
            log.debug("Read {} items from file: {}", data.size(), filename);
            return data;
        } catch (IOException e) {
            log.error("Failed to read from file {}: {}", filename, e.getMessage());
            throw new MockApiException(500, "Failed to read from file: " + e.getMessage());
        }
    }
    
    public <T> void writeToFile(String filename, List<T> data) {
        File file = new File(dataDirectory, filename);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            log.debug("Wrote {} items to file: {}", data.size(), filename);
        } catch (IOException e) {
            log.error("Failed to write to file {}: {}", filename, e.getMessage());
            throw new MockApiException(500, "Failed to write to file: " + e.getMessage());
        }
    }
    
    public <T> T readSingleFromFile(String filename, Class<T> clazz) {
        File file = new File(dataDirectory, filename);
        if (!file.exists()) {
            log.debug("Single file not found: {}", file.getPath());
            return null;
        }
        
        try {
            T data = objectMapper.readValue(file, clazz);
            log.debug("Read single item from file: {}", filename);
            return data;
        } catch (IOException e) {
            log.error("Failed to read single from file {}: {}", filename, e.getMessage());
            throw new MockApiException(500, "Failed to read from file: " + e.getMessage());
        }
    }
    
    public <T> void writeSingleToFile(String filename, T data) {
        File file = new File(dataDirectory, filename);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            log.debug("Wrote single item to file: {}", filename);
        } catch (IOException e) {
            log.error("Failed to write single to file {}: {}", filename, e.getMessage());
            throw new MockApiException(500, "Failed to write to file: " + e.getMessage());
        }
    }
    
    public boolean deleteFile(String filename) {
        File file = new File(dataDirectory, filename);
        boolean deleted = file.delete();
        if (deleted) {
            log.debug("Deleted file: {}", filename);
        } else {
            log.debug("Failed to delete file: {}", filename);
        }
        return deleted;
    }
    
    public List<String> listCollections() {
        File dir = new File(dataDirectory);
        String[] collections = dir.list((current, name) -> new File(current, name).isDirectory());
        List<String> result = collections != null ? Arrays.asList(collections) : new ArrayList<>();
        log.debug("Found {} collections", result.size());
        return result;
    }
    
    public List<String> listFiles() {
        File dir = new File(dataDirectory);
        String[] files = dir.list((current, name) -> new File(current, name).isFile());
        List<String> result = files != null ? Arrays.asList(files) : new ArrayList<>();
        log.debug("Found {} files", result.size());
        return result;
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
    
    // Get all collections
    @GetMapping("/collections")
    public ResponseEntity<ApiResponse> getAllCollections() {
        List<String> collections = fileStorage.listCollections();
        Map<String, Object> result = Map.of(
            "collections", collections,
            "count", collections.size()
        );
        return ResponseEntity.ok(ApiResponse.success(result, "Retrieved " + collections.size() + " collections"));
    }
    
    // Get all items from a collection (stored as single data.json file)
    @GetMapping("/{collection}")
    public ResponseEntity<ApiResponse> getAllItems(@PathVariable String collection) {
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        Map<String, Object> result = Map.of(
            "collection", collection,
            "items", items,
            "count", items.size()
        );
        return ResponseEntity.ok(ApiResponse.success(result, "Retrieved " + items.size() + " items from " + collection));
    }
    
    // Get single item by ID (can be stored as individual file or from collection data)
    @GetMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> getItem(@PathVariable String collection, @PathVariable String id) {
        // First try to get individual item file
        @SuppressWarnings("unchecked")
        Map<String, Object> item = fileStorage.readItemFromCollection(collection, id, Map.class);
        
        if (item != null) {
            return ResponseEntity.ok(ApiResponse.success(item));
        }
        
        // Fallback to collection data.json
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        Optional<Map<String, Object>> foundItem = items.stream()
            .filter(i -> id.equals(String.valueOf(i.get("id"))))
            .findFirst();
            
        if (foundItem.isEmpty()) {
            throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
        }
        
        return ResponseEntity.ok(ApiResponse.success(foundItem.get()));
    }
    
    // Create new item
    @PostMapping("/{collection}")
    public ResponseEntity<ApiResponse> createItem(@PathVariable String collection, 
                                                 @RequestBody Map<String, Object> item,
                                                 @RequestParam(defaultValue = "false") boolean individualFile) {
        // Generate ID if not provided
        if (!item.containsKey("id")) {
            String newId = UUID.randomUUID().toString();
            item.put("id", newId);
        }
        
        String itemId = String.valueOf(item.get("id"));
        item.put("createdAt", new Date());
        
        if (individualFile) {
            // Store as individual file
            fileStorage.writeItemToCollection(collection, itemId, item);
        } else {
            // Store in collection data.json
            List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                new TypeReference<List<Map<String, Object>>>() {});
            items.add(item);
            fileStorage.writeCollectionData(collection, items);
        }
        
        return ResponseEntity.status(201).body(ApiResponse.custom(201, "Item created successfully in " + collection, item, 
            Map.of("storage", individualFile ? "individual_file" : "collection_file")));
    }
    
    // Update item
    @PutMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> updateItem(@PathVariable String collection, 
                                                 @PathVariable String id,
                                                 @RequestBody Map<String, Object> updatedItem) {
        updatedItem.put("id", id);
        updatedItem.put("updatedAt", new Date());
        
        // Try individual file first
        @SuppressWarnings("unchecked")
        Map<String, Object> existingItem = fileStorage.readItemFromCollection(collection, id, Map.class);
        
        if (existingItem != null) {
            fileStorage.writeItemToCollection(collection, id, updatedItem);
            return ResponseEntity.ok(ApiResponse.success(updatedItem, "Item updated successfully in " + collection));
        }
        
        // Fallback to collection data.json
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            if (id.equals(String.valueOf(item.get("id")))) {
                items.set(i, updatedItem);
                fileStorage.writeCollectionData(collection, items);
                return ResponseEntity.ok(ApiResponse.success(updatedItem, "Item updated successfully in " + collection));
            }
        }
        
        throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
    }
    
    // Delete item
    @DeleteMapping("/{collection}/{id}")
    public ResponseEntity<ApiResponse> deleteItem(@PathVariable String collection, @PathVariable String id) {
        // Try individual file first
        boolean deletedFromFile = fileStorage.deleteItemFromCollection(collection, id);
        
        if (deletedFromFile) {
            return ResponseEntity.ok(ApiResponse.success(null, "Item deleted successfully from " + collection));
        }
        
        // Fallback to collection data.json
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        boolean removed = items.removeIf(item -> id.equals(String.valueOf(item.get("id"))));
        
        if (!removed) {
            throw new MockApiException(404, "Item with id " + id + " not found in " + collection);
        }
        
        fileStorage.writeCollectionData(collection, items);
        return ResponseEntity.ok(ApiResponse.success(null, "Item deleted successfully from " + collection));
    }
    
    // Get all item IDs from a collection
    @GetMapping("/{collection}/ids")
    public ResponseEntity<ApiResponse> getItemIds(@PathVariable String collection) {
        List<String> itemIds = fileStorage.getItemIdsFromCollection(collection);
        
        // Also check collection data.json for additional IDs
        List<Map<String, Object>> collectionItems = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        Set<String> allIds = new HashSet<>(itemIds);
        collectionItems.forEach(item -> {
            if (item.get("id") != null) {
                allIds.add(String.valueOf(item.get("id")));
            }
        });
        
        Map<String, Object> result = Map.of(
            "collection", collection,
            "itemIds", allIds.stream().sorted().toList(),
            "count", allIds.size(),
            "individualFiles", itemIds.size(),
            "collectionFile", collectionItems.size()
        );
        
        return ResponseEntity.ok(ApiResponse.success(result, "Retrieved " + allIds.size() + " item IDs from " + collection));
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



// Simple Search Service
// src/main/java/com/mockapi/service/SimpleSearchService.java
package com.mockapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleSearchService {
    
    private final FileStorageService fileStorageService;
    
    public Map<String, Object> search(String collection, Map<String, Object> searchCriteria, int page, int pageSize) {
        long startTime = System.currentTimeMillis();
        
        List<Map<String, Object>> allResults = new ArrayList<>();
        Map<String, String> resultSources = new HashMap<>(); // Track where each result came from
        
        // Search in collection data.json file
        List<Map<String, Object>> collectionData = fileStorageService.readCollectionData(
            collection, new TypeReference<List<Map<String, Object>>>() {});
        
        for (Map<String, Object> item : collectionData) {
            if (matches(item, searchCriteria)) {
                allResults.add(item);
                String itemId = item.get("id") != null ? String.valueOf(item.get("id")) : 
                              UUID.randomUUID().toString();
                resultSources.put(itemId, collection + "/data.json");
            }
        }
        
        // Search in individual files
        List<String> itemIds = fileStorageService.getItemIdsFromCollection(collection);
        for (String itemId : itemIds) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = fileStorageService.readItemFromCollection(collection, itemId, Map.class);
            if (item != null && matches(item, searchCriteria)) {
                // Remove from collection results if exists (prefer individual file)
                allResults.removeIf(existing -> 
                    itemId.equals(String.valueOf(existing.get("id"))));
                
                allResults.add(item);
                resultSources.put(itemId, collection + "/" + itemId + ".json");
            }
        }
        
        // Sort results by id for consistency
        allResults.sort((a, b) -> {
            String idA = String.valueOf(a.get("id"));
            String idB = String.valueOf(b.get("id"));
            return idA.compareTo(idB);
        });
        
        // Pagination
        int totalResults = allResults.size();
        int totalPages = (int) Math.ceil((double) totalResults / pageSize);
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalResults);
        
        List<Map<String, Object>> paginatedResults = 
            startIndex < totalResults ? allResults.subList(startIndex, endIndex) : new ArrayList<>();
        
        // Add source info to results
        List<Map<String, Object>> resultsWithSource = paginatedResults.stream()
            .map(item -> {
                Map<String, Object> result = new HashMap<>(item);
                String itemId = String.valueOf(item.get("id"));
                result.put("_source", resultSources.getOrDefault(itemId, "unknown"));
                return result;
            })
            .collect(Collectors.toList());
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> metadata = Map.of(
            "collection", collection,
            "totalResults", totalResults,
            "page", page,
            "pageSize", pageSize,
            "totalPages", totalPages,
            "executionTimeMs", executionTime,
            "searchTime", LocalDateTime.now(),
            "searchCriteria", searchCriteria
        );
        
        log.info("Search completed for collection '{}': {} results in {}ms", 
                collection, totalResults, executionTime);
        
        return Map.of(
            "results", resultsWithSource,
            "metadata", metadata
        );
    }
    
    private boolean matches(Map<String, Object> item, Map<String, Object> searchCriteria) {
        if (searchCriteria == null || searchCriteria.isEmpty()) {
            return true;
        }
        
        for (Map.Entry<String, Object> criterion : searchCriteria.entrySet()) {
            String fieldPath = criterion.getKey();
            Object searchValue = criterion.getValue();
            
            if (!matchesField(item, fieldPath, searchValue)) {
                return false; // ALL criteria must match (AND logic)
            }
        }
        
        return true;
    }
    
    private boolean matchesField(Map<String, Object> item, String fieldPath, Object searchValue) {
        // Handle array notation like "settings.accessList[].userId"
        if (fieldPath.contains("[]")) {
            return matchesArrayField(item, fieldPath, searchValue);
        }
        
        // Handle regular nested fields like "metadata.createdBy.userId"
        Object fieldValue = getNestedValue(item, fieldPath);
        return valuesMatch(fieldValue, searchValue);
    }
    
    private boolean matchesArrayField(Map<String, Object> item, String fieldPath, Object searchValue) {
        // Split "settings.accessList[].userId" into "settings.accessList" and "userId"
        String[] parts = fieldPath.split("\\[\\]\\.", 2);
        if (parts.length != 2) return false;
        
        String arrayPath = parts[0];
        String itemField = parts[1];
        
        Object arrayValue = getNestedValue(item, arrayPath);
        if (!(arrayValue instanceof List)) return false;
        
        List<?> array = (List<?>) arrayValue;
        for (Object arrayItem : array) {
            if (arrayItem instanceof Map) {
                Object itemValue = getNestedValue((Map<String, Object>) arrayItem, itemField);
                if (valuesMatch(itemValue, searchValue)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private Object getNestedValue(Map<String, Object> item, String fieldPath) {
        if (fieldPath == null || item == null) return null;
        
        String[] parts = fieldPath.split("\\.");
        Object current = item;
        
        for (String part : parts) {
            if (current == null) return null;
            
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    private boolean valuesMatch(Object fieldValue, Object searchValue) {
        if (fieldValue == null && searchValue == null) return true;
        if (fieldValue == null || searchValue == null) return false;
        
        // Convert both to strings for comparison to handle type differences
        String fieldStr = String.valueOf(fieldValue);
        String searchStr = String.valueOf(searchValue);
        
        // Try exact match first
        if (fieldStr.equals(searchStr)) return true;
        
        // Try case-insensitive match for strings
        if (fieldStr.equalsIgnoreCase(searchStr)) return true;
        
        // Try numeric comparison if both can be parsed as numbers
        try {
            double fieldNum = Double.parseDouble(fieldStr);
            double searchNum = Double.parseDouble(searchStr);
            return Math.abs(fieldNum - searchNum) < 0.0001; // Handle floating point precision
        } catch (NumberFormatException e) {
            // Not numbers, continue with string comparison
        }
        
        return false;
    }
}

// Simplified Search Controller
// src/main/java/com/mockapi/controller/SimpleSearchController.java
package com.mockapi.controller;

import com.mockapi.model.ApiResponse;
import com.mockapi.service.SimpleSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SimpleSearchController {
    
    private final SimpleSearchService searchService;
    
    // Main search endpoint
    @PostMapping("/{collection}")
    public ResponseEntity<ApiResponse> search(@PathVariable String collection,
                                            @RequestBody Map<String, Object> searchCriteria,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        
        Map<String, Object> result = searchService.search(collection, searchCriteria, page, pageSize);
        
        return ResponseEntity.ok(ApiResponse.success(result, 
            "Found " + ((Map<?, ?>) result.get("metadata")).get("totalResults") + " results"));
    }
    
    // Quick single field search
    @GetMapping("/{collection}")
    public ResponseEntity<ApiResponse> quickSearch(@PathVariable String collection,
                                                 @RequestParam String field,
                                                 @RequestParam String value,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        
        Map<String, Object> searchCriteria = Map.of(field, value);
        Map<String, Object> result = searchService.search(collection, searchCriteria, page, pageSize);
        
        return ResponseEntity.ok(ApiResponse.success(result, 
            "Found " + ((Map<?, ?>) result.get("metadata")).get("totalResults") + " results"));
    }
    
    // Get search examples and documentation
    @GetMapping("/examples")
    public ResponseEntity<ApiResponse> getSearchExamples() {
        Map<String, Object> examples = Map.of(
            "basic_search", Map.of(
                "description", "Find by name and status",
                "method", "POST",
                "url", "/api/search/projects",
                "body", Map.of(
                    "name", "Sample Project",
                    "status", "active"
                )
            ),
            
            "nested_field", Map.of(
                "description", "Search in nested objects",
                "method", "POST", 
                "url", "/api/search/projects",
                "body", Map.of(
                    "metadata.createdBy.userId", 42,
                    "metadata.createdBy.username", "john_doe"
                )
            ),
            
            "deep_nested", Map.of(
                "description", "Search deeply nested fields",
                "method", "POST",
                "url", "/api/search/projects", 
                "body", Map.of(
                    "metadata.createdBy.profile.firstName", "John",
                    "metadata.createdBy.profile.preferences.theme", "dark"
                )
            ),
            
            "array_search", Map.of(
                "description", "Search inside arrays using [] notation",
                "method", "POST",
                "url", "/api/search/projects",
                "body", Map.of(
                    "settings.accessList[].userId", "55",
                    "settings.accessList[].permissions", "read"
                )
            ),
            
            "mixed_search", Map.of(
                "description", "Combine different field types",
                "method", "POST",
                "url", "/api/search/projects",
                "body", Map.of(
                    "name", "Sample Project",
                    "status", "active", 
                    "metadata.createdBy.userId", 42,
                    "settings.accessList[].userId", 55
                )
            ),
            
            "quick_search", Map.of(
                "description", "Quick single field search via GET",
                "method", "GET",
                "url", "/api/search/projects?field=status&value=active"
            )
        );
        
        Map<String, Object> tips = Map.of(
            "nested_fields", "Use dots to access nested objects: 'metadata.createdBy.userId'",
            "array_search", "Use [] notation for arrays: 'settings.accessList[].userId'",
            "type_flexible", "Values are compared as both strings and numbers automatically",
            "case_insensitive", "String comparisons are case-insensitive by default",
            "source_tracking", "Results include '_source' field showing which file it came from",
            "and_logic", "All search criteria must match (AND logic)",
            "pagination", "Use page and pageSize parameters for large result sets"
        );
        
        Map<String, Object> response = Map.of(
            "examples", examples,
            "tips", tips,
            "supported_patterns", Map.of(
                "simple_field", "\"fieldName\": \"value\"",
                "nested_object", "\"parent.child.field\": \"value\"", 
                "array_element", "\"arrayField[].property\": \"value\"",
                "mixed_types", "Values can be strings, numbers, or booleans"
            )
        );
        
        return ResponseEntity.ok(ApiResponse.success(response, "Search documentation and examples"));
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
