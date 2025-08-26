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
    
    // NEW FIELD for v11
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
    
    // NEW FIELD for v11
    private List<String> applyToOperations; // ["create", "update", "both"]
}

// ========================================
// 3. UPDATE EnhancedDynamicRouteService.java - MODIFY VALIDATION METHOD
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
        
        // Determine operation type using conditional evaluation (GENERIC)
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
            List<String> missingFields = new ArrayList<>();
            for (String fieldName : requiredFieldsFromRules) {
                if (!requestBody.containsKey(fieldName) || 
                    requestBody.get(fieldName) == null || 
                    requestBody.get(fieldName).toString().trim().isEmpty()) {
                    missingFields.add(fieldName);
                }
            }
            
            if (!missingFields.isEmpty()) {
                String operation = isUpdateOperation ? "update" : "create";
                return ValidationResult.failure(400, 
                    "Missing required fields for " + operation + " operation: " + String.join(", ", missingFields),
                    Map.of(
                        "error", "Missing required fields",
                        "operation", operation,
                        "missing_fields", missingFields,
                        "message", "Required fields for " + operation + " operation: " + String.join(", ", requiredFieldsFromRules)
                    ));
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
        
        // ... rest of existing buildResponse method unchanged ...
        
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
// 4. CONFIGURATION JSON FOR YOUR USE CASE
// ========================================

/*
{
  "routeId": "call_data_v11",
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

// EXAMPLES OF MULTIPLE CONDITIONS:

// Example 1: Update requires both id AND version
{
  "updateCondition": "body_has_field:id AND body_has_field:version"
}

// Example 2: Update requires either guid OR userId  
{
  "updateCondition": "body_has_field:guid OR body_has_field:userId"
}

// Example 3: Complex condition with multiple requirements
{
  "updateCondition": "body_has_field:orderId AND body_has_field:customerId AND body_field_empty:status"
}

// Example 4: Different API examples
{
  "updateCondition": "body_has_field:userId",  // User API
  "updateCondition": "body_has_field:productId",  // Product API  
  "updateCondition": "body_has_field:orderId AND body_has_field:trackingNumber"  // Order API
}
*/

// ========================================
// 5. SUMMARY OF ALL CHANGES FOR V11
// ========================================

/*
CHANGES REQUIRED:

1. RequestConfig.java:
   - Add: private String operationDetectionCondition;

2. FieldRule.java:
   - Add: private List<String> applyToOperations;

3. EnhancedDynamicRouteService.java:
   - Update validateRequest() method signature and logic
   - Update buildResponse() method to pass requestBody to conditional checks
   - Update checkConditionalResponses() method signature
   - Update evaluateCondition() method signature and add body conditions

BENEFITS:
- Generic operation detection (no hardcoded field names)
- Configurable field rules per operation type
- Body-based conditional responses
- Reusable for any API endpoint
- Clean separation of create vs update validation
*/
