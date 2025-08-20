// ========================================
// MODEL CLASSES
// ========================================

// Field Rule for request validation
package com.mockapi.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldRule {
    private String fieldName;
    private String regexPattern;
    private int statusCode;
    private String message;
    private Object responseBody;
}

// Conditional Response configuration
@Data
@Builder
public class ConditionalResponse {
    private String condition; // e.g., "path:/users AND query:status=active"
    private int statusCode;
    private String message;
    private Object responseBody;
}

// Request Configuration
@Data
@Builder
public class RequestConfig {
    private String httpMethod; // GET, POST, PUT, DELETE
    private String urlPattern; // e.g., "/api/data/{collection}/users/{userId}/orders"
    private String collection; // which collection to use for storage
    private String storageStrategy; // "collection_file" or "individual_file"
    private String responseTemplate; // optional response template
    private Map<String, Object> defaultResponse; // default response data
    private List<String> requiredPathParams; // path parameters that must be present
    private List<String> optionalQueryParams; // optional query parameters
    private Map<String, String> paramMappings; // map path/query params to storage keys
    
    // New fields
    private List<String> requiredBodyParams; // required body parameters (supports nested: "user.departments[].name")
    private List<String> optionalBodyParams; // optional body parameters (supports nested: "preferences[].settings")
    private Map<String, String> customHeaders; // custom headers (one type)
    private List<FieldRule> fieldRules; // field validation rules with regex (supports nested: "student.departments[].building.id")
}

// Response Configuration
@Data
@Builder
public class ResponseConfig {
    private int statusCode; // HTTP status code (e.g., 200, 201, 400, 404, 500)
    private Object responseBody; // response body content
    private long delayMs; // response delay in milliseconds
    private List<ConditionalResponse> conditionalResponses; // conditional responses
    private Map<String, String> customHeaders; // custom response headers
    private List<String> fieldsToInclude; // specific fields to include (null = include all, supports nested: "profile.bio", "items[].name")
    private List<String> fieldsToExclude; // specific fields to exclude (null = exclude nothing, supports nested: "preferences[].private")
    private Map<String, Object> additionalFields; // additional fields to add to response
    private Map<String, String> fieldAliases; // field name aliases
}

// Updated Route Configuration
package com.mockapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.LocalDateTime;

@Data
@Builder
@With
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteConfig {
    private String routeId;
    private RequestConfig requestConfig;
    private ResponseConfig responseConfig;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean enabled;
}

// ========================================
// NESTED FIELD PROCESSOR SERVICE
// ========================================

// Nested Field Processor Service
package com.mockapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class NestedFieldProcessor {
    
    /**
     * Apply field inclusion/exclusion rules with support for nested objects and arrays
     * @param data The data object to filter
     * @param fieldsToInclude List of field paths to include (null = include all)
     * @param fieldsToExclude List of field paths to exclude (null = exclude nothing)
     * @return Filtered data object
     */
    public Object applyFieldFiltering(Object data, List<String> fieldsToInclude, List<String> fieldsToExclude) {
        if (data == null) {
            return null;
        }
        
        if (data instanceof Map) {
            return filterMap((Map<String, Object>) data, fieldsToInclude, fieldsToExclude);
        } else if (data instanceof List) {
            return filterList((List<Object>) data, fieldsToInclude, fieldsToExclude);
        }
        
        return data;
    }
    
    /**
     * Check if a nested field exists in the data
     * @param data The data object
     * @param fieldPath Field path like "user.preferences[].private" or "itemization.itemnum"
     * @return true if field exists
     */
    public boolean hasNestedField(Object data, String fieldPath) {
        return getNestedFieldValue(data, fieldPath) != null;
    }
    
    /**
     * Get value of a nested field
     * @param data The data object
     * @param fieldPath Field path like "user.preferences[].private"
     * @return Field value or null if not found
     */
    public Object getNestedFieldValue(Object data, String fieldPath) {
        if (data == null || fieldPath == null || fieldPath.isEmpty()) {
            return null;
        }
        
        String[] pathParts = parseFieldPath(fieldPath);
        Object current = data;
        
        for (String part : pathParts) {
            if (current == null) {
                return null;
            }
            
            if (part.endsWith("[]")) {
                // Array field
                String arrayField = part.substring(0, part.length() - 2);
                current = getArrayField(current, arrayField);
            } else {
                // Regular field
                current = getObjectField(current, part);
            }
        }
        
        return current;
    }
    
    /**
     * Set value of a nested field
     * @param data The data object
     * @param fieldPath Field path
     * @param value Value to set
     */
    public void setNestedFieldValue(Object data, String fieldPath, Object value) {
        if (data == null || fieldPath == null || fieldPath.isEmpty()) {
            return;
        }
        
        String[] pathParts = parseFieldPath(fieldPath);
        Object current = data;
        
        // Navigate to parent of target field
        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = pathParts[i];
            
            if (part.endsWith("[]")) {
                String arrayField = part.substring(0, part.length() - 2);
                current = getArrayField(current, arrayField);
            } else {
                current = getObjectField(current, part);
            }
            
            if (current == null) {
                return; // Cannot navigate further
            }
        }
        
        // Set the final field
        String finalField = pathParts[pathParts.length - 1];
        if (current instanceof Map && !finalField.endsWith("[]")) {
            ((Map<String, Object>) current).put(finalField, value);
        }
    }
    
    private Map<String, Object> filterMap(Map<String, Object> data, List<String> fieldsToInclude, List<String> fieldsToExclude) {
        Map<String, Object> result = new HashMap<>();
        
        // Apply inclusion filter first
        if (fieldsToInclude != null && !fieldsToInclude.isEmpty()) {
            for (String fieldPath : fieldsToInclude) {
                includeField(data, result, fieldPath);
            }
        } else {
            // Include all fields by default
            result.putAll(data);
            // Recursively filter nested objects
            result.replaceAll((key, value) -> applyFieldFiltering(value, null, fieldsToExclude));
        }
        
        // Apply exclusion filter
        if (fieldsToExclude != null && !fieldsToExclude.isEmpty()) {
            for (String fieldPath : fieldsToExclude) {
                excludeField(result, fieldPath);
            }
        }
        
        return result;
    }
    
    private List<Object> filterList(List<Object> data, List<String> fieldsToInclude, List<String> fieldsToExclude) {
        List<Object> result = new ArrayList<>();
        
        for (Object item : data) {
            result.add(applyFieldFiltering(item, fieldsToInclude, fieldsToExclude));
        }
        
        return result;
    }
    
    private void includeField(Map<String, Object> source, Map<String, Object> target, String fieldPath) {
        String[] pathParts = parseFieldPath(fieldPath);
        
        if (pathParts.length == 1) {
            // Simple field
            String field = pathParts[0];
            if (field.endsWith("[]")) {
                // Array field
                String arrayField = field.substring(0, field.length() - 2);
                if (source.containsKey(arrayField)) {
                    target.put(arrayField, source.get(arrayField));
                }
            } else {
                // Regular field
                if (source.containsKey(field)) {
                    target.put(field, source.get(field));
                }
            }
        } else {
            // Nested field - need to build the structure
            String topLevel = pathParts[0];
            if (source.containsKey(topLevel)) {
                if (!target.containsKey(topLevel)) {
                    target.put(topLevel, createNestedStructure(source.get(topLevel)));
                }
                
                String remainingPath = String.join(".", Arrays.copyOfRange(pathParts, 1, pathParts.length));
                if (target.get(topLevel) instanceof Map) {
                    includeField((Map<String, Object>) source.get(topLevel), 
                               (Map<String, Object>) target.get(topLevel), remainingPath);
                }
            }
        }
    }
    
    private void excludeField(Map<String, Object> data, String fieldPath) {
        String[] pathParts = parseFieldPath(fieldPath);
        
        if (pathParts.length == 1) {
            // Simple field
            String field = pathParts[0];
            if (field.endsWith("[]")) {
                // Remove entire array
                String arrayField = field.substring(0, field.length() - 2);
                data.remove(arrayField);
            } else {
                // Remove regular field
                data.remove(field);
            }
        } else {
            // Nested field
            String topLevel = pathParts[0];
            Object topLevelValue = data.get(topLevel);
            
            if (topLevelValue != null) {
                String remainingPath = String.join(".", Arrays.copyOfRange(pathParts, 1, pathParts.length));
                
                if (topLevel.endsWith("[]") && topLevelValue instanceof List) {
                    // Array of objects
                    String arrayField = topLevel.substring(0, topLevel.length() - 2);
                    List<Object> arrayData = (List<Object>) data.get(arrayField);
                    for (Object item : arrayData) {
                        if (item instanceof Map) {
                            excludeField((Map<String, Object>) item, remainingPath);
                        }
                    }
                } else if (topLevelValue instanceof Map) {
                    // Nested object
                    excludeField((Map<String, Object>) topLevelValue, remainingPath);
                }
            }
        }
    }
    
    private Object createNestedStructure(Object original) {
        if (original instanceof Map) {
            return new HashMap<>((Map<String, Object>) original);
        } else if (original instanceof List) {
            List<Object> newList = new ArrayList<>();
            for (Object item : (List<Object>) original) {
                newList.add(createNestedStructure(item));
            }
            return newList;
        }
        return original;
    }
    
    private String[] parseFieldPath(String fieldPath) {
        // Split by dots, but handle array notation
        return fieldPath.split("\\.");
    }
    
    private Object getObjectField(Object obj, String fieldName) {
        if (obj instanceof Map) {
            return ((Map<String, Object>) obj).get(fieldName);
        }
        return null;
    }
    
    private Object getArrayField(Object obj, String fieldName) {
        if (obj instanceof Map) {
            Object field = ((Map<String, Object>) obj).get(fieldName);
            if (field instanceof List) {
                return field;
            }
        }
        return null;
    }
    
    /**
     * Validate nested field using regex pattern
     * @param data The data object
     * @param fieldPath Field path like "student.departments[].building.id"
     * @param regexPattern Regex pattern to validate against
     * @return true if validation passes
     */
    public boolean validateNestedField(Object data, String fieldPath, String regexPattern) {
        Object fieldValue = getNestedFieldValue(data, fieldPath);
        
        if (fieldValue == null) {
            return true; // Null values pass validation (use required validation separately)
        }
        
        if (fieldValue instanceof List) {
            // Validate all items in array
            List<Object> arrayValues = (List<Object>) fieldValue;
            for (Object item : arrayValues) {
                if (!validateSingleValue(item, regexPattern)) {
                    return false;
                }
            }
            return true;
        } else {
            // Validate single value
            return validateSingleValue(fieldValue, regexPattern);
        }
    }
    
    private boolean validateSingleValue(Object value, String regexPattern) {
        if (value == null) {
            return true;
        }
        
        String stringValue = String.valueOf(value);
        return Pattern.matches(regexPattern, stringValue);
    }
    
    /**
     * Check if all required nested fields are present
     * @param data The data object
     * @param requiredFields List of required field paths
     * @return List of missing field paths (empty if all present)
     */
    public List<String> findMissingRequiredFields(Object data, List<String> requiredFields) {
        List<String> missing = new ArrayList<>();
        
        if (requiredFields != null) {
            for (String fieldPath : requiredFields) {
                if (!hasNestedField(data, fieldPath)) {
                    missing.add(fieldPath);
                }
            }
        }
        
        return missing;
    }
}

// ========================================
// TEMPLATE PROCESSOR SERVICE
// ========================================

// Template Processor Service
package com.mockapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateProcessorService {
    
    private final ObjectMapper objectMapper;
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    public Object processTemplate(Object template, TemplateContext context) {
        if (template == null) {
            return null;
        }
        
        if (template instanceof String) {
            return processStringTemplate((String) template, context);
        } else if (template instanceof Map) {
            return processMapTemplate((Map<String, Object>) template, context);
        } else if (template instanceof List) {
            return processListTemplate((List<Object>) template, context);
        }
        
        return template;
    }
    
    private String processStringTemplate(String template, TemplateContext context) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            String replacement = resolveVariable(variableName, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    private Map<String, Object> processMapTemplate(Map<String, Object> template, TemplateContext context) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Process key if it contains templates
            String processedKey = processStringTemplate(key, context);
            
            // Process value recursively
            Object processedValue = processTemplate(value, context);
            
            result.put(processedKey, processedValue);
        }
        
        return result;
    }
    
    private List<Object> processListTemplate(List<Object> template, TemplateContext context) {
        List<Object> result = new ArrayList<>();
        
        for (Object item : template) {
            result.add(processTemplate(item, context));
        }
        
        return result;
    }
    
    private String resolveVariable(String variableName, TemplateContext context) {
        return switch (variableName) {
            // Generated IDs
            case "created_record_guid", "generated_guid" -> UUID.randomUUID().toString();
            case "generated_6_digit_id" -> String.format("%06d", new Random().nextInt(999999));
            
            // Timestamps
            case "current_timestamp" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "current_date" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "timestamp" -> String.valueOf(System.currentTimeMillis());
            
            // Data variables
            case "created_object_with_extras" -> serializeToString(context.getCreatedObject());
            case "filtered_results" -> serializeToString(context.getFilteredResults());
            case "paginated_results" -> serializeToString(context.getPaginatedResults());
            case "paginated_filtered_results" -> serializeToString(context.getPaginatedFilteredResults());
            case "search_results" -> serializeToString(context.getSearchResults());
            case "found_user" -> serializeToString(context.getFoundObject());
            case "updated_profile" -> serializeToString(context.getUpdatedObject());
            
            // Count variables
            case "total_count" -> String.valueOf(context.getTotalCount());
            case "returned_count" -> String.valueOf(context.getReturnedCount());
            
            // Boolean variables
            case "has_more_results" -> String.valueOf(context.isHasMoreResults());
            case "has_next_page" -> String.valueOf(context.isHasNextPage());
            case "has_previous_page" -> String.valueOf(context.isHasPreviousPage());
            
            // Request variables
            case "request_body" -> serializeToString(context.getRequestBody());
            
            // Query parameter variables (dynamic)
            default -> {
                if (variableName.startsWith("query.")) {
                    String paramName = variableName.substring(6);
                    yield context.getQueryParams().getOrDefault(paramName, "");
                } else if (variableName.startsWith("path.")) {
                    String paramName = variableName.substring(5);
                    yield context.getPathParams().getOrDefault(paramName, "");
                } else if (variableName.startsWith("header.")) {
                    String headerName = variableName.substring(7);
                    yield context.getHeaders().getOrDefault(headerName, "");
                } else {
                    log.warn("Unknown template variable: {}", variableName);
                    yield "{{" + variableName + "}}"; // Return as-is if unknown
                }
            }
        };
    }
    
    private String serializeToString(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof String) {
            return (String) obj;
        }
        
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object to string", e);
            return obj.toString();
        }
    }
}

// Template Context for passing data to template processor
@Data
@Builder
class TemplateContext {
    // Created/Updated objects
    private Object createdObject;
    private Object updatedObject;
    private Object foundObject;
    
    // Search/Filter results
    private List<Object> filteredResults;
    private List<Object> paginatedResults;
    private List<Object> paginatedFilteredResults;
    private List<Object> searchResults;
    
    // Counts
    private int totalCount;
    private int returnedCount;
    
    // Pagination flags
    private boolean hasMoreResults;
    private boolean hasNextPage;
    private boolean hasPreviousPage;
    
    // Request data
    private Object requestBody;
    private Map<String, String> queryParams;
    private Map<String, String> pathParams;
    private Map<String, String> headers;
    
    // Pagination info
    private int offset;
    private int limit;
    
    // Additional context
    private String processingTimeMs;
    private Map<String, Object> customVariables;
}

// ========================================
// ENHANCED DYNAMIC ROUTE SERVICE
// ========================================

// Enhanced Dynamic Route Service
package com.mockapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedDynamicRouteService {
    
    private final FileStorageService fileStorage;
    private final TemplateProcessorService templateProcessor;
    private final NestedFieldProcessor nestedFieldProcessor;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    private static final String ROUTES_COLLECTION = "_dynamic_routes";
    
    public RouteConfig registerRoute(RouteConfig routeConfig) {
        validateRouteConfig(routeConfig);
        
        if (routeConfig.getRouteId() == null || routeConfig.getRouteId().isEmpty()) {
            routeConfig.setRouteId(generateRouteId(routeConfig));
        }
        
        if (routeExists(routeConfig.getRouteId())) {
            throw new MockApiException(409, "Route with ID " + routeConfig.getRouteId() + " already exists");
        }
        
        routeConfig.setCreatedAt(LocalDateTime.now());
        routeConfig.setEnabled(true);
        
        fileStorage.writeItemToCollection(ROUTES_COLLECTION, routeConfig.getRouteId(), routeConfig);
        
        log.info("Registered enhanced dynamic route: {} {} -> {}", 
            routeConfig.getRequestConfig().getHttpMethod(), 
            routeConfig.getRequestConfig().getUrlPattern(), 
            routeConfig.getRequestConfig().getCollection());
        
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
            RequestConfig reqConfig = route.getRequestConfig();
            if (reqConfig.getHttpMethod().equalsIgnoreCase(httpMethod) && 
                pathMatcher.match(reqConfig.getUrlPattern(), requestPath)) {
                return route;
            }
        }
        
        return null;
    }
    
    public Map<String, String> extractPathVariables(RouteConfig route, String requestPath) {
        return pathMatcher.extractUriTemplateVariables(route.getRequestConfig().getUrlPattern(), requestPath);
    }
    
    public ValidationResult validateRequest(RouteConfig route, Map<String, Object> requestBody, 
                                          HttpHeaders headers, Map<String, String> queryParams) {
        RequestConfig reqConfig = route.getRequestConfig();
        
        // Validate required body parameters (including nested fields)
        if (reqConfig.getRequiredBodyParams() != null && requestBody != null) {
            List<String> missingFields = nestedFieldProcessor.findMissingRequiredFields(requestBody, reqConfig.getRequiredBodyParams());
            if (!missingFields.isEmpty()) {
                return ValidationResult.failure(400, "Missing required parameters: " + String.join(", ", missingFields));
            }
        }
        
        // Validate custom headers
        if (reqConfig.getCustomHeaders() != null) {
            for (String headerName : reqConfig.getCustomHeaders().keySet()) {
                if (!headers.containsKey(headerName)) {
                    return ValidationResult.failure(400, "Missing required header: " + headerName);
                }
            }
        }
        
        // Validate field rules (including nested fields and arrays)
        if (reqConfig.getFieldRules() != null && requestBody != null) {
            for (FieldRule rule : reqConfig.getFieldRules()) {
                if (!nestedFieldProcessor.validateNestedField(requestBody, rule.getFieldName(), rule.getRegexPattern())) {
                    return ValidationResult.failure(rule.getStatusCode(), rule.getMessage(), rule.getResponseBody());
                }
            }
        }
        
        return ValidationResult.success();
    }
    
    public ResponseData buildResponse(RouteConfig route, Object data, String requestPath, 
                                    Map<String, String> queryParams, HttpHeaders requestHeaders,
                                    Map<String, String> pathParams, Object requestBody) {
        ResponseConfig respConfig = route.getResponseConfig();
        
        if (respConfig == null) {
            return ResponseData.builder()
                    .statusCode(200)
                    .body(data)
                    .headers(new HashMap<>())
                    .delayMs(0)
                    .build();
        }
        
        // Build template context
        TemplateContext context = buildTemplateContext(data, queryParams, pathParams, requestHeaders, requestBody);
        
        // Check conditional responses first
        ResponseData conditionalResponse = checkConditionalResponses(respConfig, requestPath, queryParams, requestHeaders, context);
        if (conditionalResponse != null) {
            return conditionalResponse;
        }
        
        // Use configured status code and response body
        int statusCode = respConfig.getStatusCode() > 0 ? respConfig.getStatusCode() : 200;
        Object responseBody = respConfig.getResponseBody() != null ? respConfig.getResponseBody() : data;
        
        // Process templates in response body
        responseBody = templateProcessor.processTemplate(responseBody, context);
        
        // Apply response transformations if responseBody is the actual data
        if (respConfig.getResponseBody() == null && data instanceof Map) {
            responseBody = transformResponseData((Map<String, Object>) data, respConfig);
        }
        
        // Add additional fields
        if (respConfig.getAdditionalFields() != null && responseBody instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) responseBody;
            Map<String, Object> processedAdditionalFields = (Map<String, Object>) templateProcessor.processTemplate(respConfig.getAdditionalFields(), context);
            bodyMap.putAll(processedAdditionalFields);
        }
        
        // Process templates in custom headers
        Map<String, String> processedHeaders = new HashMap<>();
        if (respConfig.getCustomHeaders() != null) {
            respConfig.getCustomHeaders().forEach((key, value) -> {
                String processedValue = (String) templateProcessor.processTemplate(value, context);
                processedHeaders.put(key, processedValue);
            });
        }
        
        return ResponseData.builder()
                .statusCode(statusCode)
                .body(responseBody)
                .headers(processedHeaders)
                .delayMs(respConfig.getDelayMs())
                .build();
    }
    
    private TemplateContext buildTemplateContext(Object data, Map<String, String> queryParams, 
                                               Map<String, String> pathParams, HttpHeaders requestHeaders,
                                               Object requestBody) {
        // Convert headers to Map<String, String>
        Map<String, String> headerMap = new HashMap<>();
        requestHeaders.forEach((key, values) -> {
            if (!values.isEmpty()) {
                headerMap.put(key, values.get(0));
            }
        });
        
        // Build context based on data type and operation
        TemplateContext.TemplateContextBuilder contextBuilder = TemplateContext.builder()
                .requestBody(requestBody)
                .queryParams(queryParams != null ? queryParams : new HashMap<>())
                .pathParams(pathParams != null ? pathParams : new HashMap<>())
                .headers(headerMap);
        
        // Handle different data scenarios
        if (data instanceof List) {
            List<Object> dataList = (List<Object>) data;
            contextBuilder
                    .filteredResults(dataList)
                    .searchResults(dataList)
                    .totalCount(dataList.size())
                    .returnedCount(dataList.size());
                    
            // Apply pagination if offset/limit are provided
            int offset = getIntParam(queryParams, "offset", 0);
            int limit = getIntParam(queryParams, "limit", dataList.size());
            
            if (offset > 0 || limit < dataList.size()) {
                List<Object> paginatedList = applyPagination(dataList, offset, limit);
                contextBuilder
                        .paginatedResults(paginatedList)
                        .paginatedFilteredResults(paginatedList)
                        .returnedCount(paginatedList.size())
                        .hasMoreResults(offset + limit < dataList.size())
                        .hasNextPage(offset + limit < dataList.size())
                        .hasPreviousPage(offset > 0);
            }
        } else if (data instanceof Map) {
            contextBuilder
                    .createdObject(data)
                    .updatedObject(data)
                    .foundObject(data);
        }
        
        return contextBuilder.build();
    }
    
    private List<Object> applyPagination(List<Object> data, int offset, int limit) {
        int start = Math.min(offset, data.size());
        int end = Math.min(start + limit, data.size());
        return data.subList(start, end);
    }
    
    private int getIntParam(Map<String, String> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(params.get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private ResponseData checkConditionalResponses(ResponseConfig respConfig, String requestPath, 
                                                 Map<String, String> queryParams, HttpHeaders requestHeaders,
                                                 TemplateContext context) {
        if (respConfig.getConditionalResponses() == null) {
            return null;
        }
        
        for (ConditionalResponse condResp : respConfig.getConditionalResponses()) {
            if (evaluateCondition(condResp.getCondition(), requestPath, queryParams, requestHeaders)) {
                // Process templates in conditional response
                Object processedResponseBody = templateProcessor.processTemplate(condResp.getResponseBody(), context);
                
                return ResponseData.builder()
                        .statusCode(condResp.getStatusCode())
                        .body(processedResponseBody)
                        .headers(respConfig.getCustomHeaders() != null ? respConfig.getCustomHeaders() : new HashMap<>())
                        .delayMs(respConfig.getDelayMs())
                        .build();
            }
        }
        
        return null;
    }
    
    private boolean evaluateCondition(String condition, String requestPath, 
                                    Map<String, String> queryParams, HttpHeaders requestHeaders) {
        // Simple condition parser for path, query, and header checks
        // Format: "path:/users AND query:status=active AND header:X-User-Type=admin"
        
        String[] parts = condition.split(" AND ");
        
        for (String part : parts) {
            part = part.trim();
            
            if (part.startsWith("path:")) {
                String pathPattern = part.substring(5);
                if (!requestPath.contains(pathPattern)) {
                    return false;
                }
            } else if (part.startsWith("query:")) {
                String queryCheck = part.substring(6);
                String[] keyValue = queryCheck.split("=");
                if (keyValue.length == 2) {
                    String expectedValue = queryParams.get(keyValue[0]);
                    if (!keyValue[1].equals(expectedValue)) {
                        return false;
                    }
                }
            } else if (part.startsWith("header:")) {
                String headerCheck = part.substring(7);
                String[] keyValue = headerCheck.split("=");
                if (keyValue.length == 2) {
                    String headerValue = requestHeaders.getFirst(keyValue[0]);
                    if (!keyValue[1].equals(headerValue)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    private Map<String, Object> transformResponseData(Map<String, Object> data, ResponseConfig respConfig) {
        // Use nested field processor for advanced field filtering
        Object filteredData = nestedFieldProcessor.applyFieldFiltering(data, 
                respConfig.getFieldsToInclude(), respConfig.getFieldsToExclude());
        
        Map<String, Object> result = (Map<String, Object>) filteredData;
        
        // Apply field aliases
        if (respConfig.getFieldAliases() != null) {
            Map<String, Object> aliasedResult = new HashMap<>();
            result.forEach((key, value) -> {
                String aliasKey = respConfig.getFieldAliases().getOrDefault(key, key);
                aliasedResult.put(aliasKey, value);
            });
            result = aliasedResult;
        }
        
        return result;
    }
    
    // Helper methods
    private boolean routeExists(String routeId) {
        return getRoute(routeId) != null;
    }
    
    private String generateRouteId(RouteConfig config) {
        RequestConfig reqConfig = config.getRequestConfig();
        String base = reqConfig.getHttpMethod().toLowerCase() + "_" + 
                     reqConfig.getUrlPattern().replaceAll("[^a-zA-Z0-9]", "_");
        return base + "_" + System.currentTimeMillis();
    }
    
    private void validateRouteConfig(RouteConfig config) {
        if (config.getRequestConfig() == null) {
            throw new MockApiException(400, "Request configuration is required");
        }
        
        RequestConfig reqConfig = config.getRequestConfig();
        
        if (reqConfig.getHttpMethod() == null || reqConfig.getHttpMethod().isEmpty()) {
            throw new MockApiException(400, "HTTP method is required");
        }
        
        if (!Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH").contains(reqConfig.getHttpMethod().toUpperCase())) {
            throw new MockApiException(400, "Invalid HTTP method: " + reqConfig.getHttpMethod());
        }
        
        if (reqConfig.getUrlPattern() == null || reqConfig.getUrlPattern().isEmpty()) {
            throw new MockApiException(400, "URL pattern is required");
        }
        
        if (reqConfig.getCollection() == null || reqConfig.getCollection().isEmpty()) {
            throw new MockApiException(400, "Collection is required");
        }
        
        // Validate URL pattern format
        if (!reqConfig.getUrlPattern().startsWith("/")) {
            reqConfig.setUrlPattern("/" + reqConfig.getUrlPattern());
        }
        
        // Set default storage strategy
        if (reqConfig.getStorageStrategy() == null) {
            reqConfig.setStorageStrategy("collection_file");
        }
    }
}

// Helper classes
@Data
@Builder
class ValidationResult {
    private boolean valid;
    private int statusCode;
    private String message;
    private Object responseBody;
    
    static ValidationResult success() {
        return ValidationResult.builder().valid(true).build();
    }
    
    static ValidationResult failure(int statusCode, String message) {
        return ValidationResult.builder()
                .valid(false)
                .statusCode(statusCode)
                .message(message)
                .build();
    }
    
    static ValidationResult failure(int statusCode, String message, Object responseBody) {
        return ValidationResult.builder()
                .valid(false)
                .statusCode(statusCode)
                .message(message)
                .responseBody(responseBody)
                .build();
    }
}

@Data
@Builder
class ResponseData {
    private int statusCode;
    private Object body;
    private Map<String, String> headers;
    private long delayMs;
}

// ========================================
// ENHANCED DYNAMIC ENDPOINT CONTROLLER
// ========================================

// Enhanced Dynamic Endpoint Handler Controller
package com.mockapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.ApiResponse;
import com.mockapi.model.RouteConfig;
import com.mockapi.service.EnhancedDynamicRouteService;
import com.mockapi.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/dynamic")
@RequiredArgsConstructor
@Slf4j
public class EnhancedDynamicEndpointController {
    
    private final EnhancedDynamicRouteService dynamicRouteService;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;
    
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, 
                   RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> handleDynamicRequest(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> requestBody,
            @RequestHeader HttpHeaders headers) throws InterruptedException {
        
        String httpMethod = request.getMethod();
        String requestPath = "/api/dynamic" + request.getServletPath().substring("/api/dynamic".length());
        
        log.debug("Processing enhanced dynamic request: {} {}", httpMethod, requestPath);
        
        // Find matching route configuration
        RouteConfig route = dynamicRouteService.findMatchingRoute(httpMethod, requestPath);
        if (route == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(404, "No dynamic route found for " + httpMethod + " " + requestPath));
        }
        
        log.debug("Found matching route: {}", route.getRouteId());
        
        // Extract path variables and query parameters
        Map<String, String> pathVars = extractPathVariables(route, requestPath);
        Map<String, String> queryParams = extractQueryParameters(request);
        
        // Validate request
        ValidationResult validation = dynamicRouteService.validateRequest(route, requestBody, headers, queryParams);
        if (!validation.isValid()) {
            Object responseBody = validation.getResponseBody() != null 
                    ? validation.getResponseBody() 
                    : ApiResponse.error(validation.getStatusCode(), validation.getMessage());
            
            return ResponseEntity.status(validation.getStatusCode()).body(responseBody);
        }
        
        // Process the request based on HTTP method
        Object result;
        try {
            result = switch (httpMethod.toUpperCase()) {
                case "GET" -> handleGetRequest(route, pathVars, queryParams);
                case "POST" -> handlePostRequest(route, pathVars, queryParams, requestBody);
                case "PUT" -> handlePutRequest(route, pathVars, queryParams, requestBody);
                case "DELETE" -> handleDeleteRequest(route, pathVars, queryParams);
                case "PATCH" -> handlePatchRequest(route, pathVars, queryParams, requestBody);
                default -> throw new MockApiException(405, "Method not allowed: " + httpMethod);
            };
        } catch (MockApiException e) {
            // For errors, use default error response or return standard error
            return ResponseEntity.status(e.getStatusCode())
                    .body(ApiResponse.error(e.getStatusCode(), e.getMessage()));
        }
        
        // Build response using route configuration
        ResponseData responseData = dynamicRouteService.buildResponse(route, result, requestPath, 
                queryParams, headers, pathVars, requestBody);
        
        // Apply delay if configured
        if (responseData.getDelayMs() > 0) {
            TimeUnit.MILLISECONDS.sleep(responseData.getDelayMs());
        }
        
        // Build ResponseEntity with custom headers
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(responseData.getStatusCode());
        
        if (responseData.getHeaders() != null) {
            responseData.getHeaders().forEach(responseBuilder::header);
        }
        
        return responseBuilder.body(responseData.getBody());
    }
    
    private Object handleGetRequest(RouteConfig route, Map<String, String> pathVars, 
                                   Map<String, String> queryParams) {
        String collection = route.getRequestConfig().getCollection();
        
        // If there's an 'id' path variable, try to get specific item
        if (pathVars.containsKey("id")) {
            String id = pathVars.get("id");
            Object item = getItemFromCollection(collection, id, route.getRequestConfig().getStorageStrategy());
            if (item == null) {
                throw new MockApiException(404, "Item not found: " + id);
            }
            return item;
        }
        
        // Otherwise, get all items with optional filtering
        List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
            new TypeReference<List<Map<String, Object>>>() {});
        
        // Apply basic filtering based on query parameters
        items = applyFilters(items, queryParams);
        
        return Map.of(
            "collection", collection,
            "items", items,
            "count", items.size(),
            "filters", queryParams
        );
    }
    
    private Object handlePostRequest(RouteConfig route, Map<String, String> pathVars, 
                                    Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getRequestConfig().getCollection();
        
        if (requestBody == null) {
            requestBody = new HashMap<>();
        }
        
        // Apply path variables to request body if mapping exists
        applyParameterMappings(route.getRequestConfig(), pathVars, queryParams, requestBody);
        
        // Generate ID if not provided
        if (!requestBody.containsKey("id")) {
            requestBody.put("id", UUID.randomUUID().toString());
        }
        
        requestBody.put("createdAt", new Date());
        
        String itemId = String.valueOf(requestBody.get("id"));
        boolean useIndividualFile = "individual_file".equals(route.getRequestConfig().getStorageStrategy());
        
        if (useIndividualFile) {
            fileStorage.writeItemToCollection(collection, itemId, requestBody);
        } else {
            List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                new TypeReference<List<Map<String, Object>>>() {});
            items.add(requestBody);
            fileStorage.writeCollectionData(collection, items);
        }
        
        return requestBody;
    }
    
    private Object handlePutRequest(RouteConfig route, Map<String, String> pathVars, 
                                   Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getRequestConfig().getCollection();
        
        if (!pathVars.containsKey("id")) {
            throw new MockApiException(400, "ID path variable required for PUT requests");
        }
        
        String id = pathVars.get("id");
        
        if (requestBody == null) {
            requestBody = new HashMap<>();
        }
        
        requestBody.put("id", id);
        requestBody.put("updatedAt", new Date());
        
        applyParameterMappings(route.getRequestConfig(), pathVars, queryParams, requestBody);
        
        boolean useIndividualFile = "individual_file".equals(route.getRequestConfig().getStorageStrategy());
        
        if (useIndividualFile) {
            fileStorage.writeItemToCollection(collection, id, requestBody);
        } else {
            updateItemInCollection(collection, id, requestBody);
        }
        
        return requestBody;
    }
    
    private Object handleDeleteRequest(RouteConfig route, Map<String, String> pathVars, 
                                      Map<String, String> queryParams) {
        String collection = route.getRequestConfig().getCollection();
        
        if (!pathVars.containsKey("id")) {
            throw new MockApiException(400, "ID path variable required for DELETE requests");
        }
        
        String id = pathVars.get("id");
        boolean useIndividualFile = "individual_file".equals(route.getRequestConfig().getStorageStrategy());
        
        boolean deleted;
        if (useIndividualFile) {
            deleted = fileStorage.deleteItemFromCollection(collection, id);
        } else {
            deleted = deleteItemFromCollection(collection, id);
        }
        
        if (!deleted) {
            throw new MockApiException(404, "Item not found: " + id);
        }
        
        return Map.of("deleted", true, "id", id);
    }
    
    private Object handlePatchRequest(RouteConfig route, Map<String, String> pathVars, 
                                     Map<String, String> queryParams, Map<String, Object> requestBody) {
        String collection = route.getRequestConfig().getCollection();
        
        if (!pathVars.containsKey("id")) {
            throw new MockApiException(400, "ID path variable required for PATCH requests");
        }
        
        String id = pathVars.get("id");
        
        // Get existing item
        Object existing = getItemFromCollection(collection, id, route.getRequestConfig().getStorageStrategy());
        if (existing == null) {
            throw new MockApiException(404, "Item not found: " + id);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> existingMap = (Map<String, Object>) existing;
        
        // Merge with request body
        if (requestBody != null) {
            existingMap.putAll(requestBody);
        }
        
        existingMap.put("updatedAt", new Date());
        applyParameterMappings(route.getRequestConfig(), pathVars, queryParams, existingMap);
        
        boolean useIndividualFile = "individual_file".equals(route.getRequestConfig().getStorageStrategy());
        
        if (useIndividualFile) {
            fileStorage.writeItemToCollection(collection, id, existingMap);
        } else {
            updateItemInCollection(collection, id, existingMap);
        }
        
        return existingMap;
    }
    
    private Map<String, String> extractPathVariables(RouteConfig route, String requestPath) {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        return pathMatcher.extractUriTemplateVariables(route.getRequestConfig().getUrlPattern(), requestPath);
    }
    
    // Helper methods remain the same as original...
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
        
        throw new MockApiException(404, "Item not found: " + id);
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
    
    private void applyParameterMappings(RequestConfig requestConfig, Map<String, String> pathVars, 
                                       Map<String, String> queryParams, Map<String, Object> data) {
        if (requestConfig.getParamMappings() != null) {
            requestConfig.getParamMappings().forEach((paramName, dataKey) -> {
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
}

// ========================================
// DYNAMIC ROUTE CONTROLLER
// ========================================

// Dynamic Route Controller
package com.mockapi.controller;

import com.mockapi.model.ApiResponse;
import com.mockapi.model.RouteConfig;
import com.mockapi.service.EnhancedDynamicRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class DynamicRouteController {
    
    private final EnhancedDynamicRouteService dynamicRouteService;
    
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
