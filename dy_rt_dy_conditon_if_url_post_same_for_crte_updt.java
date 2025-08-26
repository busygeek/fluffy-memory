// ========================================
// 1. UPDATE RequestConfig.java - ADD NEW FIELD
// ========================================

package com.mockapi.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RequestConfig {
    private String httpMethod;
    private String urlPattern;
    private String collection;
    private String storageStrategy;
    private List<String> requiredBodyParams;
    private List<FieldRule> fieldRules;
    private Map<String, String> customHeaders;
    private Map<String, String> paramMappings;
    
    // NEW FIELD for update detection
    private String updateCondition;
}

// ========================================
// 2. UPDATE FieldRule.java - ADD NEW FIELD
// ========================================

package com.mockapi.model;

import lombok.Data;
import java.util.List;

@Data
public class FieldRule {
    private String fieldName;
    private String regexPattern;
    private int statusCode;
    private String message;
    private Object responseBody;
    
    // NEW FIELD for operation-specific rules
    private List<String> applyToOperations; // ["create", "update", "both"]
}

// ========================================
// 3. UPDATE EnhancedDynamicRouteService.java - VALIDATION METHOD
// ========================================

package com.mockapi.service;

// ... existing imports ...

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedDynamicRouteService {
    
    // ... existing fields and methods ...
    
    // UPDATED METHOD: Replace existing validateRequest method
    public ValidationResult validateRequest(RouteConfig route, Map<String, Object> requestBody, 
                                          HttpHeaders headers, Map<String, String> queryParams) {
        RequestConfig reqConfig = route.getRequestConfig();
        
        // Determine operation type using conditional evaluation
        boolean isUpdateOperation = false;
        if (reqConfig.getUpdateCondition() != null) {
            isUpdateOperation = evaluateCondition(reqConfig.getUpdateCondition(), 
                                                "", queryParams != null ? queryParams : new HashMap<>(), 
                                                headers, requestBody);
        }
        
        // Get appropriate field rules based on operation
        List<FieldRule> applicableRules = new ArrayList<>();
        
        if (reqConfig.getFieldRules() != null) {
            for (FieldRule rule : reqConfig.getFieldRules()) {
                // Check if rule applies to current operation
                if (rule.getApplyToOperations() == null || 
                    rule.getApplyToOperations().contains(isUpdateOperation ? "update" : "create") ||
                    rule.getApplyToOperations().contains("both")) {
                    applicableRules.add(rule);
                }
            }
        }
        
        // First, validate field formats and collect required fields from rules
        List<String> requiredFieldsFromRules = new ArrayList<>();
        
        if (requestBody != null && reqConfig.getFieldRules() != null) {
            for (FieldRule rule : applicableRules) {
                String fieldName = rule.getFieldName();
                Object fieldValue = requestBody.get(fieldName);
                
                // If field is present, validate its format
                if (fieldValue != null) {
                    String stringValue = fieldValue.toString();
                    if (!stringValue.matches(rule.getRegexPattern())) {
                        return ValidationResult.failure(rule.getStatusCode(), rule.getMessage(), rule.getResponseBody());
                    }
                }
                
                // Collect field as required if it has validation rules for current operation
                requiredFieldsFromRules.add(fieldName);
            }
        }
        
        // Then check for missing required fields (from field rules)
        if (requestBody != null && !requiredFieldsFromRules.isEmpty()) {
            // Build a map of field name to rule for quick lookup
            Map<String, FieldRule> fieldRuleMap = new HashMap<>();
            for (FieldRule rule : applicableRules) {
                fieldRuleMap.put(rule.getFieldName(), rule);
            }
            
            for (String fieldName : requiredFieldsFromRules) {
                if (!requestBody.containsKey(fieldName) || 
                    requestBody.get(fieldName) == null || 
                    requestBody.get(fieldName).toString().trim().isEmpty()) {
                    
                    // Return specific field rule response if configured
                    FieldRule rule = fieldRuleMap.get(fieldName);
                    if (rule != null && rule.getStatusCode() > 0) {
                        return ValidationResult.failure(rule.getStatusCode(), rule.getMessage(), rule.getResponseBody());
                    }
                    
                    // Fallback to generic response if no specific rule response
                    String operation = isUpdateOperation ? "update" : "create";
                    return ValidationResult.failure(400, 
                        "Missing required field for " + operation + " operation: " + fieldName,
                        Map.of(
                            "error", "Missing required field",
                            "operation", operation,
                            "missing_field", fieldName,
                            "message", "Field " + fieldName + " is required for " + operation + " operation"
                        ));
                }
            }
        }
        
        // Finally, check any additional required fields from requiredBodyParams (if specified)
        if (reqConfig.getRequiredBodyParams() != null && requestBody != null) {
            List<String> missingFields = new ArrayList<>();
            for (String fieldName : reqConfig.getRequiredBodyParams()) {
                if (!requestBody.containsKey(fieldName) || 
                    requestBody.get(fieldName) == null || 
                    requestBody.get(fieldName).toString().trim().isEmpty()) {
                    missingFields.add(fieldName);
                }
            }
            
            if (!missingFields.isEmpty()) {
                return ValidationResult.failure(400, "Missing required parameters: " + String.join(", ", missingFields));
            }
        }
        
        // Validate custom headers (existing logic)
        if (reqConfig.getCustomHeaders() != null) {
            for (String headerName : reqConfig.getCustomHeaders().keySet()) {
                if (!headers.containsKey(headerName)) {
                    return ValidationResult.failure(400, "Missing required header: " + headerName);
                }
            }
        }
        
        return ValidationResult.success();
    }
    
    // UPDATED METHOD: Add requestBody parameter to method signature
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
        
        // Check conditional responses first (UPDATED with requestBody)
        ResponseData conditionalResponse = checkConditionalResponses(respConfig, requestPath, queryParams, 
                                                                   requestHeaders, context, requestBody);
        if (conditionalResponse != null) {
            return conditionalResponse;
        }
        
        // Use configured status code and response body
        int statusCode = respConfig.getStatusCode() > 0 ? respConfig.getStatusCode() : 200;
        Object responseBody = respConfig.getResponseBody() != null ? respConfig.getResponseBody() : data;
        
        // STEP A: Process regular templates first ({{variable_name}})
        responseBody = templateProcessor.processTemplate(responseBody, context);
        
        // STEP B: Process flexible dynamic patterns anywhere in response
        DynamicFieldContext dynamicContext = DynamicFieldContext.builder()
                .requestBody(requestBody)
                .queryParams(queryParams != null ? queryParams : new HashMap<>())
                .pathParams(pathParams != null ? pathParams : new HashMap<>())
                .headers(convertHttpHeadersToMap(requestHeaders))
                .foundObject(context.getFoundObject())
                .results(context.getFilteredResults())
                .build();
        
        responseBody = flexibleProcessor.processDynamicPatterns(responseBody, dynamicContext);
        
        // Apply response transformations if needed
        if (respConfig.getResponseBody() == null && data instanceof Map) {
            responseBody = transformResponseData((Map<String, Object>) data, respConfig);
        }
        
        // Add additional fields
        if (respConfig.getAdditionalFields() != null && responseBody instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) responseBody;
            Map<String, Object> processedAdditionalFields = (Map<String, Object>) templateProcessor.processTemplate(respConfig.getAdditionalFields(), context);
            
            // Also process additional fields with flexible processor
            processedAdditionalFields = (Map<String, Object>) flexibleProcessor.processDynamicPatterns(processedAdditionalFields, dynamicContext);
            
            bodyMap.putAll(processedAdditionalFields);
        }
        
        // Process templates in custom headers
        Map<String, String> processedHeaders = new HashMap<>();
        if (respConfig.getCustomHeaders() != null) {
            respConfig.getCustomHeaders().forEach((key, value) -> {
                String processedValue = (String) templateProcessor.processTemplate(value, context);
                // Also process headers with flexible processor
                processedValue = (String) flexibleProcessor.processDynamicPatterns(processedValue, dynamicContext);
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
    
    // UPDATED METHOD: Add requestBody parameter
    private ResponseData checkConditionalResponses(ResponseConfig respConfig, String requestPath, 
                                                 Map<String, String> queryParams, HttpHeaders requestHeaders,
                                                 TemplateContext context, Object requestBody) {
        if (respConfig.getConditionalResponses() == null) {
            return null;
        }
        
        for (ConditionalResponse condResp : respConfig.getConditionalResponses()) {
            if (evaluateCondition(condResp.getCondition(), requestPath, queryParams, requestHeaders, requestBody)) {
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
    
    // UPDATED METHOD: Add requestBody parameter and body-based conditions
    private boolean evaluateCondition(String condition, String requestPath, 
                                    Map<String, String> queryParams, HttpHeaders requestHeaders,
                                    Object requestBody) {
        
        // Handle body-based conditions (NEW)
        if (condition.startsWith("body_has_field:")) {
            String fieldName = condition.substring("body_has_field:".length()).trim();
            if (requestBody instanceof Map) {
                Map<String, Object> bodyMap = (Map<String, Object>) requestBody;
                Object fieldValue = bodyMap.get(fieldName);
                return fieldValue != null && !fieldValue.toString().trim().isEmpty();
            }
            return false;
        }
        
        if (condition.startsWith("body_field_empty:")) {
            String fieldName = condition.substring("body_field_empty:".length()).trim();
            if (requestBody instanceof Map) {
                Map<String, Object> bodyMap = (Map<String, Object>) requestBody;
                Object fieldValue = bodyMap.get(fieldName);
                return fieldValue == null || fieldValue.toString().trim().isEmpty();
            }
            return true; // If no body, consider field empty
        }
        
        // Existing condition logic (path, query, header) - UNCHANGED
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
    
    // ... rest of existing methods unchanged ...
}

// ========================================
// 4. UPDATE EnhancedDynamicEndpointController.java - HANDLE POST REQUEST
// ========================================

package com.mockapi.controller;

// ... existing imports ...

@RestController
@RequestMapping("/api/dynamic")
@RequiredArgsConstructor
@Slf4j
public class EnhancedDynamicEndpointController {
    
    // ... existing fields ...
    
    // UPDATED METHOD: Fix handlePostRequest for proper update handling with dynamic field extraction
    private Object handlePostRequest(RouteConfig route, Map<String, String> pathVars, 
                                    Map<String, String> queryParams, Map<String, Object> requestBody,
                                    HttpHeaders headers) {
        String collection = route.getRequestConfig().getCollection();
        
        if (requestBody == null) {
            requestBody = new HashMap<>();
        }
        
        // Determine if this is an update operation using the same logic as validation
        boolean isUpdateOperation = false;
        if (route.getRequestConfig().getUpdateCondition() != null) {
            isUpdateOperation = dynamicRouteService.evaluateCondition(
                route.getRequestConfig().getUpdateCondition(), 
                "", queryParams != null ? queryParams : new HashMap<>(), headers, requestBody
            );
        }
        
        if (isUpdateOperation) {
            // UPDATE OPERATION - extract search fields from updateCondition and search collection
            Map<String, String> searchCriteria = extractSearchCriteriaFromCondition(
                route.getRequestConfig().getUpdateCondition(), requestBody
            );
            
            if (searchCriteria.isEmpty()) {
                throw new MockApiException(400, "Unable to extract search criteria from updateCondition");
            }
            
            // Search for existing record using the extracted criteria
            Object existingRecord = findExistingRecord(collection, searchCriteria, route.getRequestConfig().getStorageStrategy());
            
            Map<String, Object> recordToSave;
            String recordId;
            
            if (existingRecord != null) {
                // Merge with existing record
                recordToSave = new HashMap<>((Map<String, Object>) existingRecord);
                recordToSave.putAll(requestBody); // Override with new values
                recordToSave.put("updatedAt", new Date());
                recordId = String.valueOf(recordToSave.get("id"));
                log.debug("Updating existing record with ID: {}", recordId);
            } else {
                // Create new record with search criteria as identifying fields
                recordToSave = new HashMap<>(requestBody);
                // Use first search field value as ID, or generate new ID if not suitable
                recordId = searchCriteria.values().iterator().next();
                if (recordId == null || recordId.trim().isEmpty()) {
                    recordId = UUID.randomUUID().toString();
                }
                recordToSave.put("id", recordId);
                recordToSave.put("createdAt", new Date());
                log.debug("Creating new record with ID from search criteria: {}", recordId);
            }
            
            // Apply parameter mappings
            applyParameterMappings(route.getRequestConfig(), pathVars, queryParams, recordToSave);
            
            // Save using determined record ID
            saveRecordToCollection(route, recordId, recordToSave);
            
            return recordToSave;
            
        } else {
            // CREATE OPERATION - generate new ID
            if (!requestBody.containsKey("id")) {
                requestBody.put("id", UUID.randomUUID().toString());
            }
            
            requestBody.put("createdAt", new Date());
            applyParameterMappings(route.getRequestConfig(), pathVars, queryParams, requestBody);
            
            // Save new record
            String itemId = String.valueOf(requestBody.get("id"));
            saveRecordToCollection(route, itemId, requestBody);
            log.debug("Created new record with generated ID: {}", itemId);
            
            return requestBody;
        }
    }
    
    // NEW METHOD: Extract search criteria from updateCondition
    private Map<String, String> extractSearchCriteriaFromCondition(String updateCondition, Map<String, Object> requestBody) {
        Map<String, String> searchCriteria = new HashMap<>();
        
        if (updateCondition == null || requestBody == null) {
            return searchCriteria;
        }
        
        // Parse condition to extract field names
        String[] conditions = updateCondition.split(" AND ");
        
        for (String condition : conditions) {
            condition = condition.trim();
            
            if (condition.startsWith("body_has_field:")) {
                String fieldName = condition.substring("body_has_field:".length()).trim();
                Object fieldValue = requestBody.get(fieldName);
                if (fieldValue != null && !fieldValue.toString().trim().isEmpty()) {
                    searchCriteria.put(fieldName, fieldValue.toString());
                }
            }
            // Add support for other condition types if needed
        }
        
        return searchCriteria;
    }
    
    // NEW METHOD: Find existing record using multiple search criteria
    private Object findExistingRecord(String collection, Map<String, String> searchCriteria, String storageStrategy) {
        if (searchCriteria.isEmpty()) {
            return null;
        }
        
        if ("individual_file".equals(storageStrategy)) {
            // For individual file storage, try each search field as potential ID
            for (String fieldValue : searchCriteria.values()) {
                Object record = fileStorage.readItemFromCollection(collection, fieldValue, Map.class);
                if (record != null) {
                    return record;
                }
            }
            return null;
        } else {
            // For collection file storage, search through all records
            List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                new TypeReference<List<Map<String, Object>>>() {});
            
            return items.stream()
                    .filter(item -> matchesSearchCriteria(item, searchCriteria))
                    .findFirst()
                    .orElse(null);
        }
    }
    
    // NEW METHOD: Check if record matches all search criteria
    private boolean matchesSearchCriteria(Map<String, Object> record, Map<String, String> searchCriteria) {
        for (Map.Entry<String, String> criteria : searchCriteria.entrySet()) {
            String fieldName = criteria.getKey();
            String expectedValue = criteria.getValue();
            Object actualValue = record.get(fieldName);
            
            if (actualValue == null || !expectedValue.equals(actualValue.toString())) {
                return false;
            }
        }
        return true;
    }
    
    // Make evaluateCondition method public so controller can access it
    // Add this method to EnhancedDynamicRouteService:
    public boolean evaluateCondition(String condition, String requestPath, 
                                   Map<String, String> queryParams, HttpHeaders requestHeaders,
                                   Object requestBody) {
        return evaluateCondition(condition, requestPath, queryParams, requestHeaders, requestBody);
    }
    
    // ... rest of existing controller methods unchanged ...
}

// ========================================
// 5. CONFIGURATION JSON FOR YOUR USE CASE
// ========================================

/*
{
  "routeId": "call_data_final",
  "enabled": true,
  "requestConfig": {
    "httpMethod": "POST",
    "urlPattern": "/call-data",
    "collection": "call_data_records",
    "storageStrategy": "individual_file",
    "updateCondition": "body_has_field:guid",
    "fieldRules": [
      {
        "fieldName": "callDataString",
        "regexPattern": ".+",
        "statusCode": 400,
        "message": "callDataString is required and cannot be null or empty",
        "applyToOperations": ["create", "update"],
        "responseBody": {
          "error": "Missing callDataString",
          "message": "callDataString is required for both create and update operations"
        }
      },
      {
        "fieldName": "guid",
        "regexPattern": "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$",
        "statusCode": 400,
        "message": "guid is required for update operations and must be valid UUID format",
        "applyToOperations": ["update"],
        "responseBody": {
          "error": "Missing or invalid guid",
          "operation": "update",
          "message": "guid is required for update operations and must be in format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        }
      }
    ]
  },
  "responseConfig": {
    "statusCode": 200,
    "conditionalResponses": [
      {
        "condition": "body_has_field:guid",
        "statusCode": 200,
        "responseBody": {
          "guid": "{requestbody.guid}"
        }
      }
    ],
    "responseBody": {
      "guid": "{{created_record_guid}}"
    }
  }
}
*/

// ========================================
// SUMMARY OF ALL CHANGES FOR FINAL VERSION
// ========================================

/*
CHANGES REQUIRED:

1. RequestConfig.java:
   - Add: private String updateCondition;

2. FieldRule.java:
   - Add: private List<String> applyToOperations;

3. EnhancedDynamicRouteService.java:
   - Update validateRequest() method with field-rules-first validation
   - Update buildResponse() method to pass requestBody to conditional checks
   - Update checkConditionalResponses() method signature
   - Update evaluateCondition() method signature and add body conditions
   - Make evaluateCondition() public for controller access

4. EnhancedDynamicEndpointController.java:
   - Update handlePostRequest() to properly handle updates by fetching existing records

BEHAVIOR:
- CREATE (no guid): Generates new GUID, creates new record
- UPDATE (with guid): Fetches existing record, merges data, saves with provided GUID
- Field validation uses specific error responses when configured
- Body-based conditional responses work correctly
- Validation checks field rules first, then determines required fields automatically
*/
