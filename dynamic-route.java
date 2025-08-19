// Enhanced Route Configuration Model
// src/main/java/com/mockapi/model/RouteConfig.java
package com.mockapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@With
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteConfig {
    private String routeId;
    private String httpMethod; // GET, POST, PUT, DELETE
    private String urlPattern; // e.g., "/api/data/{collection}/users/{userId}/orders"
    private String collection; // which collection to use for storage
    private String storageStrategy; // "collection_file" or "individual_file"
    private String responseTemplate; // optional response template
    private Map<String, Object> defaultResponse; // default response data
    private List<String> requiredPathParams; // path parameters that must be present
    private List<String> optionalQueryParams; // optional query parameters
    private Map<String, String> paramMappings; // map path/query params to storage keys
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean enabled;
    
    // Enhanced response configuration
    private ResponseConfig responseConfig;
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseConfig {
        // Success response codes for different operations
        private Integer getSuccessCode;    // Default: 200
        private Integer postSuccessCode;   // Default: 201
        private Integer putSuccessCode;    // Default: 200
        private Integer patchSuccessCode;  // Default: 200
        private Integer deleteSuccessCode; // Default: 200
        
        // Error response codes
        private Integer notFoundCode;      // Default: 404
        private Integer validationErrorCode; // Default: 400
        private Integer conflictCode;      // Default: 409
        
        // Conditional response rules
        private List<ConditionalResponse> conditionalResponses;
        
        // Custom headers to include in responses
        private Map<String, String> customHeaders;
        
        // Response delay simulation (in milliseconds)
        private Long delayMs;
        
        // Whether to include metadata in response
        private Boolean includeMetadata;
    }
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConditionalResponse {
        private String condition;     // e.g., "pathVar.userId == '123'"
        private Integer statusCode;   // Response code to use if condition matches
        private Object responseBody;  // Custom response body
        private String message;       // Custom message
        private Map<String, String> headers; // Additional headers for this condition
    }
}




// Enhanced Dynamic Route Service
// src/main/java/com/mockapi/service/DynamicRouteService.java
package com.mockapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.RouteConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicRouteService {
    
    private final FileStorageService fileStorage;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    private static final String ROUTES_COLLECTION = "_dynamic_routes";
    
    public RouteConfig registerRoute(RouteConfig routeConfig) {
        validateRouteConfig(routeConfig);
        
        if (routeConfig.getRouteId() == null || routeConfig.getRouteId().isEmpty()) {
            routeConfig.setRouteId(generateRouteId(routeConfig));
        }
        
        // Check for conflicts
        if (routeExists(routeConfig.getRouteId())) {
            throw new MockApiException(409, "Route with ID " + routeConfig.getRouteId() + " already exists");
        }
        
        routeConfig.setCreatedAt(LocalDateTime.now());
        routeConfig.setEnabled(true);
        
        // Set default response configuration if not provided
        if (routeConfig.getResponseConfig() == null) {
            routeConfig.setResponseConfig(createDefaultResponseConfig());
        } else {
            // Fill in missing default values
            fillDefaultResponseCodes(routeConfig.getResponseConfig());
        }
        
        // Store the route configuration
        fileStorage.writeItemToCollection(ROUTES_COLLECTION, routeConfig.getRouteId(), routeConfig);
        
        log.info("Registered dynamic route: {} {} -> {} with response config", 
            routeConfig.getHttpMethod(), routeConfig.getUrlPattern(), routeConfig.getCollection());
        
        return routeConfig;
    }
    
    public RouteConfig updateRoute(String routeId, RouteConfig updatedConfig) {
        RouteConfig existing = getRoute(routeId);
        if (existing == null) {
            throw new MockApiException(404, "Route not found: " + routeId);
        }
        
        updatedConfig.setRouteId(routeId);
        updatedConfig.setCreatedAt(existing.getCreatedAt());
        updatedConfig.setUpdatedAt(LocalDateTime.now());
        
        validateRouteConfig(updatedConfig);
        
        // Preserve existing response config if not provided
        if (updatedConfig.getResponseConfig() == null && existing.getResponseConfig() != null) {
            updatedConfig.setResponseConfig(existing.getResponseConfig());
        } else if (updatedConfig.getResponseConfig() != null) {
            fillDefaultResponseCodes(updatedConfig.getResponseConfig());
        }
        
        fileStorage.writeItemToCollection(ROUTES_COLLECTION, routeId, updatedConfig);
        
        log.info("Updated dynamic route: {}", routeId);
        return updatedConfig;
    }
    
    public boolean deleteRoute(String routeId) {
        boolean deleted = fileStorage.deleteItemFromCollection(ROUTES_COLLECTION, routeId);
        if (deleted) {
            log.info("Deleted dynamic route: {}", routeId);
        }
        return deleted;
    }
    
    public RouteConfig getRoute(String routeId) {
        return fileStorage.readItemFromCollection(ROUTES_COLLECTION, routeId, RouteConfig.class);
    }
    
    public List<RouteConfig> getAllRoutes() {
        List<String> routeIds = fileStorage.getItemIdsFromCollection(ROUTES_COLLECTION);
        return routeIds.stream()
                .map(this::getRoute)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    public List<RouteConfig> getActiveRoutes() {
        return getAllRoutes().stream()
                .filter(RouteConfig::isEnabled)
                .collect(Collectors.toList());
    }
    
    public RouteConfig findMatchingRoute(String httpMethod, String requestPath) {
        List<RouteConfig> activeRoutes = getActiveRoutes();
        
        for (RouteConfig route : activeRoutes) {
            if (route.getHttpMethod().equalsIgnoreCase(httpMethod) && 
                pathMatcher.match(route.getUrlPattern(), requestPath)) {
                return route;
            }
        }
        
        return null;
    }
    
    public Map<String, String> extractPathVariables(RouteConfig route, String requestPath) {
        return pathMatcher.extractUriTemplateVariables(route.getUrlPattern(), requestPath);
    }
    
    /**
     * Determines the appropriate HTTP status code for a successful operation
     */
    public int getSuccessStatusCode(RouteConfig route, String httpMethod) {
        if (route.getResponseConfig() == null) {
            return getDefaultSuccessCode(httpMethod);
        }
        
        RouteConfig.ResponseConfig config = route.getResponseConfig();
        return switch (httpMethod.toUpperCase()) {
            case "GET" -> config.getGetSuccessCode() != null ? config.getGetSuccessCode() : 200;
            case "POST" -> config.getPostSuccessCode() != null ? config.getPostSuccessCode() : 201;
            case "PUT" -> config.getPutSuccessCode() != null ? config.getPutSuccessCode() : 200;
            case "PATCH" -> config.getPatchSuccessCode() != null ? config.getPatchSuccessCode() : 200;
            case "DELETE" -> config.getDeleteSuccessCode() != null ? config.getDeleteSuccessCode() : 200;
            default -> 200;
        };
    }
    
    /**
     * Gets the configured error status code for specific error types
     */
    public int getErrorStatusCode(RouteConfig route, String errorType) {
        if (route.getResponseConfig() == null) {
            return getDefaultErrorCode(errorType);
        }
        
        RouteConfig.ResponseConfig config = route.getResponseConfig();
        return switch (errorType.toLowerCase()) {
            case "not_found" -> config.getNotFoundCode() != null ? config.getNotFoundCode() : 404;
            case "validation_error" -> config.getValidationErrorCode() != null ? config.getValidationErrorCode() : 400;
            case "conflict" -> config.getConflictCode() != null ? config.getConflictCode() : 409;
            default -> 500;
        };
    }
    
    /**
     * Evaluates conditional responses and returns the matching one if any
     */
    public RouteConfig.ConditionalResponse evaluateConditionalResponse(RouteConfig route, 
            Map<String, String> pathVars, Map<String, String> queryParams, Map<String, Object> requestBody) {
        
        if (route.getResponseConfig() == null || 
            route.getResponseConfig().getConditionalResponses() == null) {
            return null;
        }
        
        for (RouteConfig.ConditionalResponse conditional : route.getResponseConfig().getConditionalResponses()) {
            if (evaluateCondition(conditional.getCondition(), pathVars, queryParams, requestBody)) {
                return conditional;
            }
        }
        
        return null;
    }
    
    private boolean routeExists(String routeId) {
        return getRoute(routeId) != null;
    }
    
    private String generateRouteId(RouteConfig config) {
        String base = config.getHttpMethod().toLowerCase() + "_" + 
                     config.getUrlPattern().replaceAll("[^a-zA-Z0-9]", "_");
        return base + "_" + System.currentTimeMillis();
    }
    
    private void validateRouteConfig(RouteConfig config) {
        if (config.getHttpMethod() == null || config.getHttpMethod().isEmpty()) {
            throw new MockApiException(400, "HTTP method is required");
        }
        
        if (!Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH").contains(config.getHttpMethod().toUpperCase())) {
            throw new MockApiException(400, "Invalid HTTP method: " + config.getHttpMethod());
        }
        
        if (config.getUrlPattern() == null || config.getUrlPattern().isEmpty()) {
            throw new MockApiException(400, "URL pattern is required");
        }
        
        if (config.getCollection() == null || config.getCollection().isEmpty()) {
            throw new MockApiException(400, "Collection is required");
        }
        
        // Validate URL pattern format
        if (!config.getUrlPattern().startsWith("/")) {
            config.setUrlPattern("/" + config.getUrlPattern());
        }
        
        // Set default storage strategy
        if (config.getStorageStrategy() == null) {
            config.setStorageStrategy("collection_file");
        }
        
        // Validate response configuration
        if (config.getResponseConfig() != null) {
            validateResponseConfig(config.getResponseConfig());
        }
    }
    
    private void validateResponseConfig(RouteConfig.ResponseConfig responseConfig) {
        // Validate status codes are in valid HTTP range
        validateStatusCode(responseConfig.getGetSuccessCode(), "GET success code");
        validateStatusCode(responseConfig.getPostSuccessCode(), "POST success code");
        validateStatusCode(responseConfig.getPutSuccessCode(), "PUT success code");
        validateStatusCode(responseConfig.getPatchSuccessCode(), "PATCH success code");
        validateStatusCode(responseConfig.getDeleteSuccessCode(), "DELETE success code");
        validateStatusCode(responseConfig.getNotFoundCode(), "Not found code");
        validateStatusCode(responseConfig.getValidationErrorCode(), "Validation error code");
        validateStatusCode(responseConfig.getConflictCode(), "Conflict code");
        
        // Validate conditional responses
        if (responseConfig.getConditionalResponses() != null) {
            for (RouteConfig.ConditionalResponse conditional : responseConfig.getConditionalResponses()) {
                validateStatusCode(conditional.getStatusCode(), "Conditional response status code");
                if (conditional.getCondition() == null || conditional.getCondition().trim().isEmpty()) {
                    throw new MockApiException(400, "Conditional response must have a condition");
                }
            }
        }
        
        // Validate delay
        if (responseConfig.getDelayMs() != null && responseConfig.getDelayMs() < 0) {
            throw new MockApiException(400, "Response delay cannot be negative");
        }
    }
    
    private void validateStatusCode(Integer statusCode, String fieldName) {
        if (statusCode != null && (statusCode < 100 || statusCode > 599)) {
            throw new MockApiException(400, fieldName + " must be between 100 and 599");
        }
    }
    
    private RouteConfig.ResponseConfig createDefaultResponseConfig() {
        return RouteConfig.ResponseConfig.builder()
                .getSuccessCode(200)
                .postSuccessCode(201)
                .putSuccessCode(200)
                .patchSuccessCode(200)
                .deleteSuccessCode(200)
                .notFoundCode(404)
                .validationErrorCode(400)
                .conflictCode(409)
                .includeMetadata(true)
                .build();
    }
    
    private void fillDefaultResponseCodes(RouteConfig.ResponseConfig config) {
        if (config.getGetSuccessCode() == null) config.setGetSuccessCode(200);
        if (config.getPostSuccessCode() == null) config.setPostSuccessCode(201);
        if (config.getPutSuccessCode() == null) config.setPutSuccessCode(200);
        if (config.getPatchSuccessCode() == null) config.setPatchSuccessCode(200);
        if (config.getDeleteSuccessCode() == null) config.setDeleteSuccessCode(200);
        if (config.getNotFoundCode() == null) config.setNotFoundCode(404);
        if (config.getValidationErrorCode() == null) config.setValidationErrorCode(400);
        if (config.getConflictCode() == null) config.setConflictCode(409);
        if (config.getIncludeMetadata() == null) config.setIncludeMetadata(true);
    }
    
    private int getDefaultSuccessCode(String httpMethod) {
        return switch (httpMethod.toUpperCase()) {
            case "POST" -> 201;
            default -> 200;
        };
    }
    
    private int getDefaultErrorCode(String errorType) {
        return switch (errorType.toLowerCase()) {
            case "not_found" -> 404;
            case "validation_error" -> 400;
            case "conflict" -> 409;
            default -> 500;
        };
    }
    
    /**
     * Simple condition evaluation - supports basic expressions like:
     * - pathVar.userId == '123'
     * - queryParam.status == 'active'
     * - requestBody.type == 'premium'
     */
    private boolean evaluateCondition(String condition, Map<String, String> pathVars, 
            Map<String, String> queryParams, Map<String, Object> requestBody) {
        
        if (condition == null || condition.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Simple string-based evaluation for basic conditions
            condition = condition.trim();
            
            if (condition.contains("pathVar.")) {
                return evaluatePathVarCondition(condition, pathVars);
            } else if (condition.contains("queryParam.")) {
                return evaluateQueryParamCondition(condition, queryParams);
            } else if (condition.contains("requestBody.") && requestBody != null) {
                return evaluateRequestBodyCondition(condition, requestBody);
            }
            
            // If no specific pattern matches, return false
            return false;
            
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}", condition, e);
            return false;
        }
    }
    
    private boolean evaluatePathVarCondition(String condition, Map<String, String> pathVars) {
        // Extract variable name and expected value
        // Format: pathVar.variableName == 'expectedValue'
        String[] parts = condition.split("==");
        if (parts.length != 2) return false;
        
        String varPart = parts[0].trim().replace("pathVar.", "");
        String expectedValue = parts[1].trim().replaceAll("'", "");
        
        String actualValue = pathVars.get(varPart);
        return actualValue != null && actualValue.equals(expectedValue);
    }
    
    private boolean evaluateQueryParamCondition(String condition, Map<String, String> queryParams) {
        // Extract parameter name and expected value
        String[] parts = condition.split("==");
        if (parts.length != 2) return false;
        
        String paramPart = parts[0].trim().replace("queryParam.", "");
        String expectedValue = parts[1].trim().replaceAll("'", "");
        
        String actualValue = queryParams.get(paramPart);
        return actualValue != null && actualValue.equals(expectedValue);
    }
    
    private boolean evaluateRequestBodyCondition(String condition, Map<String, Object> requestBody) {
        // Extract field name and expected value
        String[] parts = condition.split("==");
        if (parts.length != 2) return false;
        
        String fieldPart = parts[0].trim().replace("requestBody.", "");
        String expectedValue = parts[1].trim().replaceAll("'", "");
        
        Object actualValue = requestBody.get(fieldPart);
        return actualValue != null && actualValue.toString().equals(expectedValue);
    }
}



// Enhanced Dynamic Endpoint Handler Controller
// src/main/java/com/mockapi/controller/DynamicEndpointController.java
package com.mockapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.ApiResponse;
import com.mockapi.model.RouteConfig;
import com.mockapi.service.DynamicRouteService;
import com.mockapi.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/dynamic")
@RequiredArgsConstructor
@Slf4j
public class DynamicEndpointController {
    
    private final DynamicRouteService dynamicRouteService;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;
    
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, 
                   RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<ApiResponse> handleDynamicRequest(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> requestBody) {
        
        String httpMethod = request.getMethod();
        String requestPath = "/api/dynamic" + request.getServletPath().substring("/api/dynamic".length());
        
        log.debug("Processing dynamic request: {} {}", httpMethod, requestPath);
        
        // Find matching route configuration
        RouteConfig route = dynamicRouteService.findMatchingRoute(httpMethod, requestPath);
        if (route == null) {
            throw new MockApiException(404, "No dynamic route found for " + httpMethod + " " + requestPath);
        }
        
        log.debug("Found matching route: {}", route.getRouteId());
        
        // Apply response delay if configured
        applyResponseDelay(route);
        
        // Extract path variables and query parameters
        Map<String, String> pathVars = dynamicRouteService.extractPathVariables(route, requestPath);
        Map<String, String> queryParams = extractQueryParameters(request);
        
        // Check for conditional responses first
        RouteConfig.ConditionalResponse conditionalResponse = dynamicRouteService
                .evaluateConditionalResponse(route, pathVars, queryParams, requestBody);
        
        if (conditionalResponse != null) {
            return handleConditionalResponse(conditionalResponse, route, pathVars, queryParams);
        }
        
        // Process the request based on HTTP method
        DynamicResponse dynamicResponse = switch (httpMethod.toUpperCase()) {
            case "GET" -> handleGetRequest(route, pathVars, queryParams);
            case "POST" -> handlePostRequest(route, pathVars, queryParams, requestBody);
            case "PUT" -> handlePutRequest(route, pathVars, queryParams, requestBody);
            case "DELETE" -> handleDeleteRequest(route, pathVars, queryParams);
            case "PATCH" -> handlePatchRequest(route, pathVars, queryParams, requestBody);
            default -> throw new MockApiException(405, "Method not allowed: " + httpMethod);
        };
        
        // Get the appropriate success status code
        int statusCode = dynamicRouteService.getSuccessStatusCode(route, httpMethod);
        
        // Build response with custom headers and metadata
        return buildResponseEntity(route, dynamicResponse, statusCode, pathVars, queryParams, httpMethod);
    }
    
    private DynamicResponse handleGetRequest(RouteConfig route, Map<String, String> pathVars, 
                                           Map<String, String> queryParams) {
        String collection = route.getCollection();
        
        try {
            // If there's an 'id' path variable, try to get specific item
            if (pathVars.containsKey("id")) {
                String id = pathVars.get("id");
                Object item = getItemFromCollection(collection, id, route.getStorageStrategy());
                
                if (item == null) {
                    int notFoundCode = dynamicRouteService.getErrorStatusCode(route, "not_found");
                    throw new MockApiException(notFoundCode, "Item not found: " + id);
                }
                
                return new DynamicResponse(item, "Item retrieved successfully");
            }
            
            // Otherwise, get all items with optional filtering
            List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                new TypeReference<List<Map<String, Object>>>() {});
            
            // Apply basic filtering based on query parameters
            items = applyFilters(items, queryParams);
            
            Map<String, Object> result = Map.of(
                "collection", collection,
                "items", items,
                "count", items.size(),
                "filters", queryParams
            );
            
            return new DynamicResponse(result, "Collection retrieved successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in GET request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private DynamicResponse handlePostRequest(RouteConfig route, Map<String, String> pathVars, 
                                            Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getCollection();
        
        try {
            if (requestBody == null) {
                requestBody = new HashMap<>();
            }
            
            // Apply path variables to request body if mapping exists
            applyParameterMappings(route, pathVars, queryParams, requestBody);
            
            // Generate ID if not provided
            if (!requestBody.containsKey("id")) {
                requestBody.put("id", UUID.randomUUID().toString());
            }
            
            // Check for conflicts if ID was provided
            String itemId = String.valueOf(requestBody.get("id"));
            if (getItemFromCollection(collection, itemId, route.getStorageStrategy()) != null) {
                int conflictCode = dynamicRouteService.getErrorStatusCode(route, "conflict");
                throw new MockApiException(conflictCode, "Item with ID " + itemId + " already exists");
            }
            
            requestBody.put("createdAt", new Date());
            
            boolean useIndividualFile = "individual_file".equals(route.getStorageStrategy());
            
            if (useIndividualFile) {
                fileStorage.writeItemToCollection(collection, itemId, requestBody);
            } else {
                List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                    new TypeReference<List<Map<String, Object>>>() {});
                items.add(requestBody);
                fileStorage.writeCollectionData(collection, items);
            }
            
            return new DynamicResponse(requestBody, "Item created successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in POST request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private DynamicResponse handlePutRequest(RouteConfig route, Map<String, String> pathVars, 
                                           Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getCollection();
        
        try {
            if (!pathVars.containsKey("id")) {
                int validationCode = dynamicRouteService.getErrorStatusCode(route, "validation_error");
                throw new MockApiException(validationCode, "ID path variable required for PUT requests");
            }
            
            String id = pathVars.get("id");
            
            if (requestBody == null) {
                requestBody = new HashMap<>();
            }
            
            requestBody.put("id", id);
            requestBody.put("updatedAt", new Date());
            
            applyParameterMappings(route, pathVars, queryParams, requestBody);
            
            boolean useIndividualFile = "individual_file".equals(route.getStorageStrategy());
            
            if (useIndividualFile) {
                fileStorage.writeItemToCollection(collection, id, requestBody);
            } else {
                updateItemInCollection(collection, id, requestBody);
            }
            
            return new DynamicResponse(requestBody, "Item updated successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in PUT request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private DynamicResponse handleDeleteRequest(RouteConfig route, Map<String, String> pathVars, 
                                              Map<String, String> queryParams) {
        String collection = route.getCollection();
        
        try {
            if (!pathVars.containsKey("id")) {
                int validationCode = dynamicRouteService.getErrorStatusCode(route, "validation_error");
                throw new MockApiException(validationCode, "ID path variable required for DELETE requests");
            }
            
            String id = pathVars.get("id");
            boolean useIndividualFile = "individual_file".equals(route.getStorageStrategy());
            
            boolean deleted;
            if (useIndividualFile) {
                deleted = fileStorage.deleteItemFromCollection(collection, id);
            } else {
                deleted = deleteItemFromCollection(collection, id);
            }
            
            if (!deleted) {
                int notFoundCode = dynamicRouteService.getErrorStatusCode(route, "not_found");
                throw new MockApiException(notFoundCode, "Item not found: " + id);
            }
            
            Map<String, Object> result = Map.of("deleted", true, "id", id);
            return new DynamicResponse(result, "Item deleted successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in DELETE request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private DynamicResponse handlePatchRequest(RouteConfig route, Map<String, String> pathVars, 
                                             Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getCollection();
        
        try {
            if (!pathVars.containsKey("id")) {
                int validationCode = dynamicRouteService.getErrorStatusCode(route, "validation_error");
                throw new MockApiException(validationCode, "ID path variable required for PATCH requests");
            }
            
            String id = pathVars.get("id");
            
            // Get existing item
            Object existing = getItemFromCollection(collection, id, route.getStorageStrategy());
            if (existing == null) {
                int notFoundCode = dynamicRouteService.getErrorStatusCode(route, "not_found");
                throw new MockApiException(notFoundCode, "Item not found: " + id);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> existingMap = (Map<String, Object>) existing;
            
            // Merge with request body
            if (requestBody != null) {
                existingMap.putAll(requestBody);
            }
            
            existingMap.put("updatedAt", new Date());
            applyParameterMappings(route, pathVars, queryParams, existingMap);
            
            boolean useIndividualFile = "individual_file".equals(route.getStorageStrategy());
            
            if (useIndividualFile) {
                fileStorage.writeItemToCollection(collection, id, existingMap);
            } else {
                updateItemInCollection(collection, id, existingMap);
            }
            
            return new DynamicResponse(existingMap, "Item partially updated successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in PATCH request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private ResponseEntity<ApiResponse> handleConditionalResponse(
            RouteConfig.ConditionalResponse conditionalResponse, RouteConfig route,
            Map<String, String> pathVars, Map<String, String> queryParams) {
        
        log.debug("Applying conditional response with status code: {}", conditionalResponse.getStatusCode());
        
        // Create custom headers
        HttpHeaders headers = new HttpHeaders();
        addCustomHeaders(headers, route.getResponseConfig());
        if (conditionalResponse.getHeaders() != null) {
            conditionalResponse.getHeaders().forEach(headers::add);
        }
        
        // Create response body
        Object responseBody = conditionalResponse.getResponseBody();
        if (responseBody == null) {
            responseBody = Map.of("message", conditionalResponse.getMessage() != null ? 
                conditionalResponse.getMessage() : "Conditional response triggered");
        }
        
        // Build metadata if enabled
        Map<String, Object> metadata = null;
        if (route.getResponseConfig() != null && 
            Boolean.TRUE.equals(route.getResponseConfig().getIncludeMetadata())) {
            metadata = Map.of(
                "route", route.getRouteId(),
                "collection", route.getCollection(),
                "conditionalResponse", true,
                "pathVariables", pathVars,
                "queryParameters", queryParams
            );
        }
        
        ApiResponse apiResponse = ApiResponse.custom(
            conditionalResponse.getStatusCode(),
            conditionalResponse.getMessage(),
            responseBody,
            metadata
        );
        
        return ResponseEntity.status(conditionalResponse.getStatusCode())
                .headers(headers)
                .body(apiResponse);
    }
    
    private ResponseEntity<ApiResponse> buildResponseEntity(RouteConfig route, DynamicResponse dynamicResponse, 
            int statusCode, Map<String, String> pathVars, Map<String, String> queryParams, String httpMethod) {
        
        // Create custom headers
        HttpHeaders headers = new HttpHeaders();
        addCustomHeaders(headers, route.getResponseConfig());
        
        // Build metadata if enabled
        Map<String, Object> metadata = null;
        if (route.getResponseConfig() != null && 
            Boolean.TRUE.equals(route.getResponseConfig().getIncludeMetadata())) {
            metadata = Map.of(
                "route", route.getRouteId(),
                "collection", route.getCollection(),
                "pathVariables", pathVars,
                "queryParameters", queryParams,
                "httpMethod", httpMethod,
                "statusCode", statusCode
            );
        }
        
        ApiResponse apiResponse = ApiResponse.custom(
            statusCode,
            dynamicResponse.getMessage(),
            dynamicResponse.getData(),
            metadata
        );
        
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(apiResponse);
    }
    
    private void applyResponseDelay(RouteConfig route) {
        if (route.getResponseConfig() != null && 
            route.getResponseConfig().getDelayMs() != null && 
            route.getResponseConfig().getDelayMs() > 0) {
            
            try {
                log.debug("Applying response delay of {}ms for route: {}", 
                    route.getResponseConfig().getDelayMs(), route.getRouteId());
                Thread.sleep(route.getResponseConfig().getDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Response delay interrupted for route: {}", route.getRouteId());
            }
        }
    }
    
    private void addCustomHeaders(HttpHeaders headers, RouteConfig.ResponseConfig responseConfig) {
        if (responseConfig != null && responseConfig.getCustomHeaders() != null) {
            responseConfig.getCustomHeaders().forEach(headers::add);
        }
    }
    
    private Object getItemFromCollection(String collection, String id, String storageStrategy) {
        if ("individual_file".equals(storageStrategy)) {
            return fileStorage.readItemFromCollection(collection, id, Map.class);
        } else {
            List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                new TypeReference<List<Map<String, Object>>>() {});
            
            return items.stream()
                    .filter(item -> id.equals(String.valueOf(item.get("id"))))
                    .findFirst()
                    .orElse(null);
        }
    }
    
    private void updateItemInCollection(String collection, String id, Map<String, Object> updatedItem) {
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            if (id.equals(String.valueOf(item.get("id")))) {
                items.set(i, updatedItem);
                fileStorage.writeCollectionData(collection, items);
                return;
            }
        }
        
        int notFoundCode = dynamicRouteService.getErrorStatusCode(
            dynamicRouteService.findMatchingRoute("PUT", "/dummy"), "not_found");
        throw new MockApiException(notFoundCode, "Item not found: " + id);
    }
    
    private boolean deleteItemFromCollection(String collection, String id) {
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        boolean removed = items.removeIf(item -> id.equals(String.valueOf(item.get("id"))));
        
        if (removed) {
            fileStorage.writeCollectionData(collection, items);
        }
        
        return removed;
    }
    
    private Map<String, String> extractQueryParameters(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        if (request.getQueryString() != null) {
            String[] pairs = request.getQueryString().split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    try {
                        queryParams.put(
                            java.net.URLDecoder.decode(keyValue[0], "UTF-8"),
                            java.net.URLDecoder.decode(keyValue[1], "UTF-8")
                        );
                    } catch (Exception e) {
                        log.warn("Failed to decode query parameter: {}", pair);
                    }
                }
            }
        }
        return queryParams;
    }
    
    private void applyParameterMappings(RouteConfig route, Map<String, String> pathVars, 
                                       Map<String, String> queryParams, Map<String, Object> data) {
        if (route.getParamMappings() != null) {
            route.getParamMappings().forEach((paramName, dataKey) -> {
                String value = pathVars.getOrDefault(paramName, queryParams.get(paramName));
                if (value != null) {
                    data.put(dataKey, value);
                }
            });
        }
    }
    
    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> items, 
                                                  Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return items;
        }
        
        return items.stream()
                .filter(item -> queryParams.entrySet().stream()
                        .allMatch(entry -> {
                            String key = entry.getKey();
                            String value = entry.getValue();
                            Object itemValue = item.get(key);
                            return itemValue != null && itemValue.toString().contains(value);
                        }))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    // Helper class to encapsulate response data and message
    private static class DynamicResponse {
        private final Object data;
        private final String message;
        
        public DynamicResponse(Object data, String message) {
            this.data = data;
            this.message = message;
        }
        
        public Object getData() {
            return data;
        }
        
        public String getMessage() {
            return message;
        }
    }
}


























// Enhanced Route Configuration Model
// src/main/java/com/mockapi/model/RouteConfig.java
package com.mockapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@With
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteConfig {
    private String routeId;
    private String httpMethod; // GET, POST, PUT, DELETE
    private String urlPattern; // e.g., "/api/data/{collection}/users/{userId}/orders"
    private String collection; // which collection to use for storage
    private String storageStrategy; // "collection_file" or "individual_file"
    private String responseTemplate; // optional response template
    private Map<String, Object> defaultResponse; // default response data
    private List<String> requiredPathParams; // path parameters that must be present
    private List<String> optionalQueryParams; // optional query parameters
    private Map<String, String> paramMappings; // map path/query params to storage keys
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean enabled;
    
    // Enhanced response configuration
    private ResponseConfig responseConfig;
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseConfig {
        // Success response codes for different operations
        private Integer getSuccessCode;    // Default: 200
        private Integer postSuccessCode;   // Default: 201
        private Integer putSuccessCode;    // Default: 200
        private Integer patchSuccessCode;  // Default: 200
        private Integer deleteSuccessCode; // Default: 200
        
        // Error response codes
        private Integer notFoundCode;      // Default: 404
        private Integer validationErrorCode; // Default: 400
        private Integer conflictCode;      // Default: 409
        
        // Conditional response rules
        private List<ConditionalResponse> conditionalResponses;
        
        // Custom headers to include in responses
        private Map<String, String> customHeaders;
        
        // Response delay simulation (in milliseconds)
        private Long delayMs;
        
        // Whether to include metadata in response
        private Boolean includeMetadata;
    }
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConditionalResponse {
        private String condition;     // e.g., "pathVar.userId == '123'"
        private Integer statusCode;   // Response code to use if condition matches
        private Object responseBody;  // Custom response body
        private String message;       // Custom message
        private Map<String, String> headers; // Additional headers for this condition
    }
}

// ================================================================================

// Enhanced Dynamic Route Service
// src/main/java/com/mockapi/service/DynamicRouteService.java
package com.mockapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.RouteConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicRouteService {
    
    private final FileStorageService fileStorage;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    private static final String ROUTES_COLLECTION = "_dynamic_routes";
    
    public RouteConfig registerRoute(RouteConfig routeConfig) {
        validateRouteConfig(routeConfig);
        
        if (routeConfig.getRouteId() == null || routeConfig.getRouteId().isEmpty()) {
            routeConfig.setRouteId(generateRouteId(routeConfig));
        }
        
        // Check for conflicts
        if (routeExists(routeConfig.getRouteId())) {
            throw new MockApiException(409, "Route with ID " + routeConfig.getRouteId() + " already exists");
        }
        
        routeConfig.setCreatedAt(LocalDateTime.now());
        routeConfig.setEnabled(true);
        
        // Set default response configuration if not provided
        if (routeConfig.getResponseConfig() == null) {
            routeConfig.setResponseConfig(createDefaultResponseConfig());
        } else {
            // Fill in missing default values
            fillDefaultResponseCodes(routeConfig.getResponseConfig());
        }
        
        // Store the route configuration
        fileStorage.writeItemToCollection(ROUTES_COLLECTION, routeConfig.getRouteId(), routeConfig);
        
        log.info("Registered dynamic route: {} {} -> {} with response config", 
            routeConfig.getHttpMethod(), routeConfig.getUrlPattern(), routeConfig.getCollection());
        
        return routeConfig;
    }
    
    public RouteConfig updateRoute(String routeId, RouteConfig updatedConfig) {
        RouteConfig existing = getRoute(routeId);
        if (existing == null) {
            throw new MockApiException(404, "Route not found: " + routeId);
        }
        
        updatedConfig.setRouteId(routeId);
        updatedConfig.setCreatedAt(existing.getCreatedAt());
        updatedConfig.setUpdatedAt(LocalDateTime.now());
        
        validateRouteConfig(updatedConfig);
        
        // Preserve existing response config if not provided
        if (updatedConfig.getResponseConfig() == null && existing.getResponseConfig() != null) {
            updatedConfig.setResponseConfig(existing.getResponseConfig());
        } else if (updatedConfig.getResponseConfig() != null) {
            fillDefaultResponseCodes(updatedConfig.getResponseConfig());
        }
        
        fileStorage.writeItemToCollection(ROUTES_COLLECTION, routeId, updatedConfig);
        
        log.info("Updated dynamic route: {}", routeId);
        return updatedConfig;
    }
    
    public boolean deleteRoute(String routeId) {
        boolean deleted = fileStorage.deleteItemFromCollection(ROUTES_COLLECTION, routeId);
        if (deleted) {
            log.info("Deleted dynamic route: {}", routeId);
        }
        return deleted;
    }
    
    public RouteConfig getRoute(String routeId) {
        return fileStorage.readItemFromCollection(ROUTES_COLLECTION, routeId, RouteConfig.class);
    }
    
    public List<RouteConfig> getAllRoutes() {
        List<String> routeIds = fileStorage.getItemIdsFromCollection(ROUTES_COLLECTION);
        return routeIds.stream()
                .map(this::getRoute)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    public List<RouteConfig> getActiveRoutes() {
        return getAllRoutes().stream()
                .filter(RouteConfig::isEnabled)
                .collect(Collectors.toList());
    }
    
    public RouteConfig findMatchingRoute(String httpMethod, String requestPath) {
        List<RouteConfig> activeRoutes = getActiveRoutes();
        
        for (RouteConfig route : activeRoutes) {
            if (route.getHttpMethod().equalsIgnoreCase(httpMethod) && 
                pathMatcher.match(route.getUrlPattern(), requestPath)) {
                return route;
            }
        }
        
        return null;
    }
    
    public Map<String, String> extractPathVariables(RouteConfig route, String requestPath) {
        return pathMatcher.extractUriTemplateVariables(route.getUrlPattern(), requestPath);
    }
    
    /**
     * Determines the appropriate HTTP status code for a successful operation
     */
    public int getSuccessStatusCode(RouteConfig route, String httpMethod) {
        if (route.getResponseConfig() == null) {
            return getDefaultSuccessCode(httpMethod);
        }
        
        RouteConfig.ResponseConfig config = route.getResponseConfig();
        return switch (httpMethod.toUpperCase()) {
            case "GET" -> config.getGetSuccessCode() != null ? config.getGetSuccessCode() : 200;
            case "POST" -> config.getPostSuccessCode() != null ? config.getPostSuccessCode() : 201;
            case "PUT" -> config.getPutSuccessCode() != null ? config.getPutSuccessCode() : 200;
            case "PATCH" -> config.getPatchSuccessCode() != null ? config.getPatchSuccessCode() : 200;
            case "DELETE" -> config.getDeleteSuccessCode() != null ? config.getDeleteSuccessCode() : 200;
            default -> 200;
        };
    }
    
    /**
     * Gets the configured error status code for specific error types
     */
    public int getErrorStatusCode(RouteConfig route, String errorType) {
        if (route.getResponseConfig() == null) {
            return getDefaultErrorCode(errorType);
        }
        
        RouteConfig.ResponseConfig config = route.getResponseConfig();
        return switch (errorType.toLowerCase()) {
            case "not_found" -> config.getNotFoundCode() != null ? config.getNotFoundCode() : 404;
            case "validation_error" -> config.getValidationErrorCode() != null ? config.getValidationErrorCode() : 400;
            case "conflict" -> config.getConflictCode() != null ? config.getConflictCode() : 409;
            default -> 500;
        };
    }
    
    /**
     * Evaluates conditional responses and returns the matching one if any
     */
    public RouteConfig.ConditionalResponse evaluateConditionalResponse(RouteConfig route, 
            Map<String, String> pathVars, Map<String, String> queryParams, Map<String, Object> requestBody) {
        
        if (route.getResponseConfig() == null || 
            route.getResponseConfig().getConditionalResponses() == null) {
            return null;
        }
        
        for (RouteConfig.ConditionalResponse conditional : route.getResponseConfig().getConditionalResponses()) {
            if (evaluateCondition(conditional.getCondition(), pathVars, queryParams, requestBody)) {
                return conditional;
            }
        }
        
        return null;
    }
    
    private boolean routeExists(String routeId) {
        return getRoute(routeId) != null;
    }
    
    private String generateRouteId(RouteConfig config) {
        String base = config.getHttpMethod().toLowerCase() + "_" + 
                     config.getUrlPattern().replaceAll("[^a-zA-Z0-9]", "_");
        return base + "_" + System.currentTimeMillis();
    }
    
    private void validateRouteConfig(RouteConfig config) {
        if (config.getHttpMethod() == null || config.getHttpMethod().isEmpty()) {
            throw new MockApiException(400, "HTTP method is required");
        }
        
        if (!Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH").contains(config.getHttpMethod().toUpperCase())) {
            throw new MockApiException(400, "Invalid HTTP method: " + config.getHttpMethod());
        }
        
        if (config.getUrlPattern() == null || config.getUrlPattern().isEmpty()) {
            throw new MockApiException(400, "URL pattern is required");
        }
        
        if (config.getCollection() == null || config.getCollection().isEmpty()) {
            throw new MockApiException(400, "Collection is required");
        }
        
        // Validate URL pattern format
        if (!config.getUrlPattern().startsWith("/")) {
            config.setUrlPattern("/" + config.getUrlPattern());
        }
        
        // Set default storage strategy
        if (config.getStorageStrategy() == null) {
            config.setStorageStrategy("collection_file");
        }
        
        // Validate response configuration
        if (config.getResponseConfig() != null) {
            validateResponseConfig(config.getResponseConfig());
        }
    }
    
    private void validateResponseConfig(RouteConfig.ResponseConfig responseConfig) {
        // Validate status codes are in valid HTTP range
        validateStatusCode(responseConfig.getGetSuccessCode(), "GET success code");
        validateStatusCode(responseConfig.getPostSuccessCode(), "POST success code");
        validateStatusCode(responseConfig.getPutSuccessCode(), "PUT success code");
        validateStatusCode(responseConfig.getPatchSuccessCode(), "PATCH success code");
        validateStatusCode(responseConfig.getDeleteSuccessCode(), "DELETE success code");
        validateStatusCode(responseConfig.getNotFoundCode(), "Not found code");
        validateStatusCode(responseConfig.getValidationErrorCode(), "Validation error code");
        validateStatusCode(responseConfig.getConflictCode(), "Conflict code");
        
        // Validate conditional responses
        if (responseConfig.getConditionalResponses() != null) {
            for (RouteConfig.ConditionalResponse conditional : responseConfig.getConditionalResponses()) {
                validateStatusCode(conditional.getStatusCode(), "Conditional response status code");
                if (conditional.getCondition() == null || conditional.getCondition().trim().isEmpty()) {
                    throw new MockApiException(400, "Conditional response must have a condition");
                }
            }
        }
        
        // Validate delay
        if (responseConfig.getDelayMs() != null && responseConfig.getDelayMs() < 0) {
            throw new MockApiException(400, "Response delay cannot be negative");
        }
    }
    
    private void validateStatusCode(Integer statusCode, String fieldName) {
        if (statusCode != null && (statusCode < 100 || statusCode > 599)) {
            throw new MockApiException(400, fieldName + " must be between 100 and 599");
        }
    }
    
    private RouteConfig.ResponseConfig createDefaultResponseConfig() {
        return RouteConfig.ResponseConfig.builder()
                .getSuccessCode(200)
                .postSuccessCode(201)
                .putSuccessCode(200)
                .patchSuccessCode(200)
                .deleteSuccessCode(200)
                .notFoundCode(404)
                .validationErrorCode(400)
                .conflictCode(409)
                .includeMetadata(true)
                .build();
    }
    
    private void fillDefaultResponseCodes(RouteConfig.ResponseConfig config) {
        if (config.getGetSuccessCode() == null) config.setGetSuccessCode(200);
        if (config.getPostSuccessCode() == null) config.setPostSuccessCode(201);
        if (config.getPutSuccessCode() == null) config.setPutSuccessCode(200);
        if (config.getPatchSuccessCode() == null) config.setPatchSuccessCode(200);
        if (config.getDeleteSuccessCode() == null) config.setDeleteSuccessCode(200);
        if (config.getNotFoundCode() == null) config.setNotFoundCode(404);
        if (config.getValidationErrorCode() == null) config.setValidationErrorCode(400);
        if (config.getConflictCode() == null) config.setConflictCode(409);
        if (config.getIncludeMetadata() == null) config.setIncludeMetadata(true);
    }
    
    private int getDefaultSuccessCode(String httpMethod) {
        return switch (httpMethod.toUpperCase()) {
            case "POST" -> 201;
            default -> 200;
        };
    }
    
    private int getDefaultErrorCode(String errorType) {
        return switch (errorType.toLowerCase()) {
            case "not_found" -> 404;
            case "validation_error" -> 400;
            case "conflict" -> 409;
            default -> 500;
        };
    }
    
    /**
     * Simple condition evaluation - supports basic expressions like:
     * - pathVar.userId == '123'
     * - queryParam.status == 'active'
     * - requestBody.type == 'premium'
     */
    private boolean evaluateCondition(String condition, Map<String, String> pathVars, 
            Map<String, String> queryParams, Map<String, Object> requestBody) {
        
        if (condition == null || condition.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Simple string-based evaluation for basic conditions
            condition = condition.trim();
            
            if (condition.contains("pathVar.")) {
                return evaluatePathVarCondition(condition, pathVars);
            } else if (condition.contains("queryParam.")) {
                return evaluateQueryParamCondition(condition, queryParams);
            } else if (condition.contains("requestBody.") && requestBody != null) {
                return evaluateRequestBodyCondition(condition, requestBody);
            }
            
            // If no specific pattern matches, return false
            return false;
            
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}", condition, e);
            return false;
        }
    }
    
    private boolean evaluatePathVarCondition(String condition, Map<String, String> pathVars) {
        // Extract variable name and expected value
        // Format: pathVar.variableName == 'expectedValue'
        String[] parts = condition.split("==");
        if (parts.length != 2) return false;
        
        String varPart = parts[0].trim().replace("pathVar.", "");
        String expectedValue = parts[1].trim().replaceAll("'", "");
        
        String actualValue = pathVars.get(varPart);
        return actualValue != null && actualValue.equals(expectedValue);
    }
    
    private boolean evaluateQueryParamCondition(String condition, Map<String, String> queryParams) {
        // Extract parameter name and expected value
        String[] parts = condition.split("==");
        if (parts.length != 2) return false;
        
        String paramPart = parts[0].trim().replace("queryParam.", "");
        String expectedValue = parts[1].trim().replaceAll("'", "");
        
        String actualValue = queryParams.get(paramPart);
        return actualValue != null && actualValue.equals(expectedValue);
    }
    
    private boolean evaluateRequestBodyCondition(String condition, Map<String, Object> requestBody) {
        // Extract field name and expected value
        String[] parts = condition.split("==");
        if (parts.length != 2) return false;
        
        String fieldPart = parts[0].trim().replace("requestBody.", "");
        String expectedValue = parts[1].trim().replaceAll("'", "");
        
        Object actualValue = requestBody.get(fieldPart);
        return actualValue != null && actualValue.toString().equals(expectedValue);
    }
}

// ================================================================================

// Dynamic Route Controller
// src/main/java/com/mockapi/controller/DynamicRouteController.java
package com.mockapi.controller;

import com.mockapi.model.ApiResponse;
import com.mockapi.model.RouteConfig;
import com.mockapi.service.DynamicRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class DynamicRouteController {
    
    private final DynamicRouteService dynamicRouteService;
    
    @PostMapping
    public ResponseEntity<ApiResponse> registerRoute(@RequestBody RouteConfig routeConfig) {
        RouteConfig registered = dynamicRouteService.registerRoute(routeConfig);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(registered, "Route registered successfully"));
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse> getAllRoutes(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<RouteConfig> routes = activeOnly ? 
            dynamicRouteService.getActiveRoutes() : 
            dynamicRouteService.getAllRoutes();
            
        Map<String, Object> result = Map.of(
            "routes", routes,
            "count", routes.size(),
            "activeOnly", activeOnly
        );
        
        return ResponseEntity.ok(ApiResponse.success(result, "Retrieved " + routes.size() + " routes"));
    }
    
    @GetMapping("/{routeId}")
    public ResponseEntity<ApiResponse> getRoute(@PathVariable String routeId) {
        RouteConfig route = dynamicRouteService.getRoute(routeId);
        if (route == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(route));
    }
    
    @PutMapping("/{routeId}")
    public ResponseEntity<ApiResponse> updateRoute(@PathVariable String routeId, 
                                                  @RequestBody RouteConfig routeConfig) {
        RouteConfig updated = dynamicRouteService.updateRoute(routeId, routeConfig);
        return ResponseEntity.ok(ApiResponse.success(updated, "Route updated successfully"));
    }
    
    @DeleteMapping("/{routeId}")
    public ResponseEntity<ApiResponse> deleteRoute(@PathVariable String routeId) {
        boolean deleted = dynamicRouteService.deleteRoute(routeId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Route deleted successfully"));
    }
    
    @PatchMapping("/{routeId}/toggle")
    public ResponseEntity<ApiResponse> toggleRoute(@PathVariable String routeId) {
        RouteConfig route = dynamicRouteService.getRoute(routeId);
        if (route == null) {
            return ResponseEntity.notFound().build();
        }
        
        route.setEnabled(!route.isEnabled());
        RouteConfig updated = dynamicRouteService.updateRoute(routeId, route);
        
        return ResponseEntity.ok(ApiResponse.success(updated, 
            "Route " + (updated.isEnabled() ? "enabled" : "disabled")));
    }
}

// ================================================================================

// Enhanced Dynamic Endpoint Handler Controller
// src/main/java/com/mockapi/controller/DynamicEndpointController.java
package com.mockapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.ApiResponse;
import com.mockapi.model.RouteConfig;
import com.mockapi.service.DynamicRouteService;
import com.mockapi.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/dynamic")
@RequiredArgsConstructor
@Slf4j
public class DynamicEndpointController {
    
    private final DynamicRouteService dynamicRouteService;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;
    
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, 
                   RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<ApiResponse> handleDynamicRequest(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> requestBody) {
        
        String httpMethod = request.getMethod();
        String requestPath = "/api/dynamic" + request.getServletPath().substring("/api/dynamic".length());
        
        log.debug("Processing dynamic request: {} {}", httpMethod, requestPath);
        
        // Find matching route configuration
        RouteConfig route = dynamicRouteService.findMatchingRoute(httpMethod, requestPath);
        if (route == null) {
            throw new MockApiException(404, "No dynamic route found for " + httpMethod + " " + requestPath);
        }
        
        log.debug("Found matching route: {}", route.getRouteId());
        
        // Apply response delay if configured
        applyResponseDelay(route);
        
        // Extract path variables and query parameters
        Map<String, String> pathVars = dynamicRouteService.extractPathVariables(route, requestPath);
        Map<String, String> queryParams = extractQueryParameters(request);
        
        // Check for conditional responses first
        RouteConfig.ConditionalResponse conditionalResponse = dynamicRouteService
                .evaluateConditionalResponse(route, pathVars, queryParams, requestBody);
        
        if (conditionalResponse != null) {
            return handleConditionalResponse(conditionalResponse, route, pathVars, queryParams);
        }
        
        // Process the request based on HTTP method
        DynamicResponse dynamicResponse = switch (httpMethod.toUpperCase()) {
            case "GET" -> handleGetRequest(route, pathVars, queryParams);
            case "POST" -> handlePostRequest(route, pathVars, queryParams, requestBody);
            case "PUT" -> handlePutRequest(route, pathVars, queryParams, requestBody);
            case "DELETE" -> handleDeleteRequest(route, pathVars, queryParams);
            case "PATCH" -> handlePatchRequest(route, pathVars, queryParams, requestBody);
            default -> throw new MockApiException(405, "Method not allowed: " + httpMethod);
        };
        
        // Get the appropriate success status code
        int statusCode = dynamicRouteService.getSuccessStatusCode(route, httpMethod);
        
        // Build response with custom headers and metadata
        return buildResponseEntity(route, dynamicResponse, statusCode, pathVars, queryParams, httpMethod);
    }
    
    private DynamicResponse handleGetRequest(RouteConfig route, Map<String, String> pathVars, 
                                           Map<String, String> queryParams) {
        String collection = route.getCollection();
        
        try {
            // If there's an 'id' path variable, try to get specific item
            if (pathVars.containsKey("id")) {
                String id = pathVars.get("id");
                Object item = getItemFromCollection(collection, id, route.getStorageStrategy());
                
                if (item == null) {
                    int notFoundCode = dynamicRouteService.getErrorStatusCode(route, "not_found");
                    throw new MockApiException(notFoundCode, "Item not found: " + id);
                }
                
                return new DynamicResponse(item, "Item retrieved successfully");
            }
            
            // Otherwise, get all items with optional filtering
            List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                new TypeReference<List<Map<String, Object>>>() {});
            
            // Apply basic filtering based on query parameters
            items = applyFilters(items, queryParams);
            
            Map<String, Object> result = Map.of(
                "collection", collection,
                "items", items,
                "count", items.size(),
                "filters", queryParams
            );
            
            return new DynamicResponse(result, "Collection retrieved successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in GET request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private DynamicResponse handlePostRequest(RouteConfig route, Map<String, String> pathVars, 
                                            Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getCollection();
        
        try {
            if (requestBody == null) {
                requestBody = new HashMap<>();
            }
            
            // Apply path variables to request body if mapping exists
            applyParameterMappings(route, pathVars, queryParams, requestBody);
            
            // Generate ID if not provided
            if (!requestBody.containsKey("id")) {
                requestBody.put("id", UUID.randomUUID().toString());
            }
            
            // Check for conflicts if ID was provided
            String itemId = String.valueOf(requestBody.get("id"));
            if (getItemFromCollection(collection, itemId, route.getStorageStrategy()) != null) {
                int conflictCode = dynamicRouteService.getErrorStatusCode(route, "conflict");
                throw new MockApiException(conflictCode, "Item with ID " + itemId + " already exists");
            }
            
            requestBody.put("createdAt", new Date());
            
            boolean useIndividualFile = "individual_file".equals(route.getStorageStrategy());
            
            if (useIndividualFile) {
                fileStorage.writeItemToCollection(collection, itemId, requestBody);
            } else {
                List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                    new TypeReference<List<Map<String, Object>>>() {});
                items.add(requestBody);
                fileStorage.writeCollectionData(collection, items);
            }
            
            return new DynamicResponse(requestBody, "Item created successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in POST request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private DynamicResponse handlePutRequest(RouteConfig route, Map<String, String> pathVars, 
                                           Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getCollection();
        
        try {
            if (!pathVars.containsKey("id")) {
                int validationCode = dynamicRouteService.getErrorStatusCode(route, "validation_error");
                throw new MockApiException(validationCode, "ID path variable required for PUT requests");
            }
            
            String id = pathVars.get("id");
            
            if (requestBody == null) {
                requestBody = new HashMap<>();
            }
            
            requestBody.put("id", id);
            requestBody.put("updatedAt", new Date());
            
            applyParameterMappings(route, pathVars, queryParams, requestBody);
            
            boolean useIndividualFile = "individual_file".equals(route.getStorageStrategy());
            
            if (useIndividualFile) {
                fileStorage.writeItemToCollection(collection, id, requestBody);
            } else {
                updateItemInCollection(collection, id, requestBody);
            }
            
            return new DynamicResponse(requestBody, "Item updated successfully");
            
        } catch (MockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in PUT request for route: {}", route.getRouteId(), e);
            throw new MockApiException(500, "Internal server error: " + e.getMessage());
        }
    }
    
    private DynamicResponse handleDeleteRequest(RouteConfig route, Map<String, String> pathVars, 
                                              Map<String, String> queryParams) {
        String collection = route.getCollection();
        
        try {
            if (!pathVars.containsKey("id")) {
