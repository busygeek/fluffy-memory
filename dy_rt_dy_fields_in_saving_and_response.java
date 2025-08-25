// ========================================
// UPDATED ENHANCED ENDPOINT CONTROLLER - ALL METHODS SUPPORT
// ========================================

@RestController
@RequestMapping("/api/dynamic")
@RequiredArgsConstructor
@Slf4j
public class EnhancedDynamicEndpointController {
    
    private final EnhancedDynamicRouteService dynamicRouteService;
    private final FileStorageService fileStorage;
    private final TemplateProcessorService templateProcessor;
    private final FlexibleDynamicFieldProcessor flexibleProcessor;
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
                case "GET" -> handleGetRequest(route, pathVars, queryParams, headers);
                case "POST" -> handlePostRequest(route, pathVars, queryParams, requestBody, headers);
                case "PUT" -> handlePutRequest(route, pathVars, queryParams, requestBody, headers);
                case "DELETE" -> handleDeleteRequest(route, pathVars, queryParams, headers);
                case "PATCH" -> handlePatchRequest(route, pathVars, queryParams, requestBody, headers);
                default -> throw new MockApiException(405, "Method not allowed: " + httpMethod);
            };
        } catch (MockApiException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(ApiResponse.error(e.getStatusCode(), e.getMessage()));
        }
        
        // BUILD RESPONSE WITH FLEXIBLE DYNAMIC FIELDS (ALL METHODS)
        ResponseData responseData = dynamicRouteService.buildResponse(route, result, requestPath, 
                queryParams, headers, pathVars, requestBody);
        
        // SAVE GENERATED VALUES FOR POST AND PUT METHODS
        if ("POST".equals(httpMethod) || "PUT".equals(httpMethod)) {
            if (result instanceof Map) {
                Map<String, Object> recordData = (Map<String, Object>) result;
                String itemId = String.valueOf(recordData.get("id"));
                
                // Extract generated values from response
                Map<String, String> generatedValues = extractGeneratedValues(responseData.getBody());
                
                if (!generatedValues.isEmpty()) {
                    updateRecordWithGeneratedValues(route, itemId, generatedValues);
                    // Also update the result object
                    recordData.putAll(generatedValues);
                }
            }
        }
        
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
    
    // ========================================
    // UPDATED HTTP METHOD HANDLERS
    // ========================================
    
    // UPDATED: GET with flexible dynamic fields support
    private Object handleGetRequest(RouteConfig route, Map<String, String> pathVars, 
                                   Map<String, String> queryParams, HttpHeaders headers) {
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
    
    // UPDATED: POST with flexible dynamic fields and saving
    private Object handlePostRequest(RouteConfig route, Map<String, String> pathVars, 
                                    Map<String, String> queryParams, Map<String, Object> requestBody,
                                    HttpHeaders headers) {
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
        
        // Save initial record
        String itemId = String.valueOf(requestBody.get("id"));
        saveRecordToCollection(route, itemId, requestBody);
        
        return requestBody;
    }
    
    // UPDATED: PUT with flexible dynamic fields and saving
    private Object handlePutRequest(RouteConfig route, Map<String, String> pathVars, 
                                   Map<String, String> queryParams, Map<String, Object> requestBody,
                                   HttpHeaders headers) {
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
        
        // Save/update record
        saveRecordToCollection(route, id, requestBody);
        
        return requestBody;
    }
    
    // UPDATED: DELETE with flexible dynamic fields support
    private Object handleDeleteRequest(RouteConfig route, Map<String, String> pathVars, 
                                      Map<String, String> queryParams, HttpHeaders headers) {
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
    
    // UPDATED: PATCH with flexible dynamic fields support
    private Object handlePatchRequest(RouteConfig route, Map<String, String> pathVars, 
                                     Map<String, String> queryParams, Map<String, Object> requestBody,
                                     HttpHeaders headers) {
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
        
        // Save updated record
        saveRecordToCollection(route, id, existingMap);
        
        return existingMap;
    }
    
    // ========================================
    // HELPER METHODS
    // ========================================
    
    // UPDATED: Generic method to save record to collection
    private void saveRecordToCollection(RouteConfig route, String itemId, Map<String, Object> recordData) {
        String collection = route.getRequestConfig().getCollection();
        boolean useIndividualFile = "individual_file".equals(route.getRequestConfig().getStorageStrategy());
        
        try {
            if (useIndividualFile) {
                fileStorage.writeItemToCollection(collection, itemId, recordData);
            } else {
                List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                    new TypeReference<List<Map<String, Object>>>() {});
                
                // Remove existing record if it exists (for PUT)
                items.removeIf(item -> itemId.equals(String.valueOf(item.get("id"))));
                
                // Add the record
                items.add(recordData);
                fileStorage.writeCollectionData(collection, items);
            }
            log.debug("Saved record {} to collection {}", itemId, collection);
        } catch (Exception e) {
            log.error("Failed to save record {} to collection {}", itemId, collection, e);
        }
    }
    
    // Extract generated values from response body recursively
    private Map<String, String> extractGeneratedValues(Object responseBody) {
        Map<String, String> generatedValues = new HashMap<>();
        extractGeneratedValuesRecursive(responseBody, "", generatedValues);
        return generatedValues;
    }
    
    private void extractGeneratedValuesRecursive(Object obj, String path, Map<String, String> values) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String currentPath = path.isEmpty() ? key : path + "." + key;
                
                if (value instanceof String) {
                    String strValue = (String) value;
                    // Only save values that look like they were generated
                    if (looksLikeGeneratedValue(strValue)) {
                        values.put(key, strValue);
                    }
                } else if (value instanceof Map || value instanceof List) {
                    extractGeneratedValuesRecursive(value, currentPath, values);
                }
            }
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            for (int i = 0; i < list.size(); i++) {
                extractGeneratedValuesRecursive(list.get(i), path + "[" + i + "]", values);
            }
        }
    }
    
    private boolean looksLikeGeneratedValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // Check if it looks like a generated value
        return value.matches(".*\\d{3,}.*") ||           // Has 3+ consecutive digits
               value.matches(".*[A-Z]{3,}.*") ||         // Has 3+ consecutive uppercase letters
               value.contains("-") ||                    // Contains dashes (common in IDs)
               value.matches(".*\\d+-\\d+.*") ||         // Number-dash-number pattern
               value.length() > 15 ||                    // Long strings are likely generated
               value.matches("^[A-Z0-9]{6,}$") ||        // Uppercase alphanumeric codes
               value.contains("@") ||                    // Email addresses
               value.startsWith("http") ||               // URLs
               value.matches(".*\\d{4,}.*");             // Has 4+ consecutive digits
    }
    
    // Update record in collection with generated values
    private void updateRecordWithGeneratedValues(RouteConfig route, String itemId, Map<String, String> generatedValues) {
        String collection = route.getRequestConfig().getCollection();
        boolean useIndividualFile = "individual_file".equals(route.getRequestConfig().getStorageStrategy());
        
        try {
            if (useIndividualFile) {
                // Read existing record
                Map<String, Object> existingRecord = fileStorage.readItemFromCollection(collection, itemId, Map.class);
                if (existingRecord != null) {
                    // Add generated values
                    existingRecord.putAll(generatedValues);
                    // Save back to storage
                    fileStorage.writeItemToCollection(collection, itemId, existingRecord);
                    log.debug("Updated individual file record {} with generated values: {}", itemId, generatedValues);
                }
            } else {
                // Read entire collection
                List<Map<String, Object>> items = fileStorage.readCollectionData(collection, 
                    new TypeReference<List<Map<String, Object>>>() {});
                
                // Find and update the record
                for (Map<String, Object> item : items) {
                    if (itemId.equals(String.valueOf(item.get("id")))) {
                        item.putAll(generatedValues);
                        break;
                    }
                }
                
                // Save entire collection back
                fileStorage.writeCollectionData(collection, items);
                log.debug("Updated collection record {} with generated values: {}", itemId, generatedValues);
            }
        } catch (Exception e) {
            log.error("Failed to update record {} with generated values", itemId, e);
        }
    }
    
    // ... rest of existing helper methods unchanged (extractPathVariables, extractQueryParameters, etc.) ...
    
    private Map<String, String> extractPathVariables(RouteConfig route, String requestPath) {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        return pathMatcher.extractUriTemplateVariables(route.getRequestConfig().getUrlPattern(), requestPath);
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
// USAGE EXAMPLES FOR ALL METHODS
// ========================================

/*
GET Request with Flexible Dynamic Fields:
{
  "routeId": "get_users_with_dynamic",
  "requestConfig": {
    "httpMethod": "GET",
    "urlPattern": "/api/users/{id}",
    "collection": "users"
  },
  "responseConfig": {
    "statusCode": 200,
    "responseBody": {
      "user": "{{found_user}}",
      "generatedCode": "\\d{6}",                    // -> "123456"
      "trackingId": "TRK-{8 random chars}",        // -> "TRK-A3B9K2M5"
      "timestamp": "{datetime}",                    // -> "2024-08-20T14:30:25"
      "userAgent": "{header.user-agent}"           // -> actual header value
    }
  }
}

POST Request with Generated Values Saved:
{
  "routeId": "post_contacts_with_saving",
  "requestConfig": {
    "httpMethod": "POST",
    "urlPattern": "/api/contacts",
    "collection": "contacts"
  },
  "responseConfig": {
    "statusCode": 201,
    "responseBody": {
      "success": true,
      "contactId": "\\d{6}",                       // -> "123456" (saved to collection)
      "referenceCode": "REF-{6 random chars}",    // -> "REF-A3B9K2" (saved to collection)
      "userInfo": "{requestbody.name}",            // -> request field value
      "nested": {
        "trackingId": "[A-Z]{3}[0-9]{3}",         // -> "ABC123" (saved to collection)
        "timestamp": "{timestamp}"                 // -> current timestamp
      }
    }
  }
}

PUT Request with Generated Values Saved:
{
  "routeId": "put_users_with_saving",
  "requestConfig": {
    "httpMethod": "PUT", 
    "urlPattern": "/api/users/{id}",
    "collection": "users"
  },
  "responseConfig": {
    "statusCode": 200,
    "responseBody": {
      "updated": true,
      "updateCode": "UPD-\\d{4}",                 // -> "UPD-1234" (saved to collection)
      "versionId": "{uuid}",                      // -> UUID (saved to collection)
      "modifiedBy": "{header.x-user-id}",         // -> header value
      "meta": {
        "processId": "[A-Z0-9]{8}",              // -> "A3B9K2M5" (saved to collection)
        "updatedAt": "{datetime}"                 // -> current datetime
      }
    }
  }
}

DELETE Request with Dynamic Response:
{
  "routeId": "delete_with_dynamic",
  "requestConfig": {
    "httpMethod": "DELETE",
    "urlPattern": "/api/items/{id}",
    "collection": "items"
  },
  "responseConfig": {
    "statusCode": 200,
    "responseBody": {
      "deleted": true,
      "deletionId": "{uuid}",                     // -> UUID
      "confirmationCode": "DEL-\\d{6}",           // -> "DEL-123456"
      "deletedBy": "{header.x-user-id}",          // -> header value
      "timestamp": "{datetime}"                   // -> current datetime
    }
  }
}

RESULT:
- GET: Dynamic fields in response (not saved to collection)
- POST: Dynamic fields generated AND saved to collection for searching
- PUT: Dynamic fields generated AND saved to collection for searching  
- DELETE: Dynamic fields in response (record deleted, so no saving)
- PATCH: Similar to PUT, can generate and save dynamic fields
*/
