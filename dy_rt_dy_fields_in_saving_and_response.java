// ========================================
// 1. UPDATED RESPONSE CONFIG MODEL
// ========================================

package com.mockapi.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ResponseConfig {
    private int statusCode; // HTTP status code (e.g., 200, 201, 400, 404, 500)
    private Object responseBody; // response body content - can contain flexible patterns
    private long delayMs; // response delay in milliseconds
    private List<ConditionalResponse> conditionalResponses; // conditional responses
    private Map<String, String> customHeaders; // custom response headers
    private List<String> fieldsToInclude; // specific fields to include (null = include all)
    private List<String> fieldsToExclude; // specific fields to exclude (null = exclude nothing)
    private Map<String, Object> additionalFields; // additional fields to add to response
    private Map<String, String> fieldAliases; // field name aliases
}

// ========================================
// 2. DYNAMIC FIELD CONTEXT
// ========================================

package com.mockapi.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DynamicFieldContext {
    private Object requestBody;
    private Map<String, String> queryParams;
    private Map<String, String> pathParams; 
    private Map<String, String> headers;
    private Object foundObject;
    private List<Object> results;
}

// ========================================
// 3. FLEXIBLE DYNAMIC FIELD PROCESSOR
// ========================================

package com.mockapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockapi.model.DynamicFieldContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FlexibleDynamicFieldProcessor {
    
    private final Random random = new Random();
    private final ObjectMapper objectMapper;
    
    // Patterns to detect dynamic fields
    private static final Pattern CURLY_BRACE_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    public FlexibleDynamicFieldProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process any object recursively to find and replace dynamic patterns
     */
    public Object processDynamicPatterns(Object obj, DynamicFieldContext context) {
        if (obj == null) {
            return null;
        }
        
        if (obj instanceof String) {
            return processStringValue((String) obj, context);
        } else if (obj instanceof Map) {
            return processMap((Map<String, Object>) obj, context);
        } else if (obj instanceof List) {
            return processList((List<Object>) obj, context);
        } else {
            return obj; // Return primitives as-is
        }
    }
    
    private String processStringValue(String value, DynamicFieldContext context) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        
        // Check if it's a dynamic pattern with curly braces
        if (value.contains("{") && value.contains("}")) {
            return processCurlyBracePattern(value, context);
        }
        
        // Check if it looks like a regex pattern
        if (looksLikeRegexPattern(value)) {
            return generateFromRegexPattern(value, context);
        }
        
        return value;
    }
    
    private Map<String, Object> processMap(Map<String, Object> map, DynamicFieldContext context) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Process the value recursively
            Object processedValue = processDynamicPatterns(value, context);
            result.put(key, processedValue);
        }
        
        return result;
    }
    
    private List<Object> processList(List<Object> list, DynamicFieldContext context) {
        List<Object> result = new ArrayList<>();
        
        for (Object item : list) {
            result.add(processDynamicPatterns(item, context));
        }
        
        return result;
    }
    
    private String processCurlyBracePattern(String pattern, DynamicFieldContext context) {
        Matcher matcher = CURLY_BRACE_PATTERN.matcher(pattern);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            String replacement = resolvePlaceholder(placeholder, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    private boolean looksLikeRegexPattern(String value) {
        // Simple heuristics to detect regex patterns
        return value.length() > 3 && (
            value.contains("\\d") || 
            value.contains("\\w") || 
            value.contains("\\s") ||
            value.contains("[") || 
            value.contains("]") ||
            value.contains("^") || 
            value.contains("$") ||
            value.contains("+") || 
            value.contains("*") ||
            value.contains("?") ||
            value.contains("{") ||
            value.contains("|") ||
            (value.startsWith("^") && value.endsWith("$"))
        );
    }
    
    private String generateFromRegexPattern(String regexPattern, DynamicFieldContext context) {
        try {
            return generateFromRegex(regexPattern);
        } catch (Exception e) {
            log.warn("Failed to generate from regex pattern '{}': {}", regexPattern, e.getMessage());
            return regexPattern; // Return original if generation fails
        }
    }
    
    private String generateFromRegex(String regex) {
        // Simple regex-to-data generator
        return switch (regex) {
            // Common patterns
            case "\\d{3}-\\d{2}-\\d{4}" -> generateNumbers(3) + "-" + generateNumbers(2) + "-" + generateNumbers(4);
            case "^[A-Z][a-z]+$" -> generateCapitalizedWord();
            case "\\w+" -> generateRandomString(8);
            case "\\d+" -> generateNumbers(6);
            case "[A-Z]{3}" -> generateLetters(3);
            case "[0-9]{4}" -> generateNumbers(4);
            case "[a-zA-Z0-9]+" -> generateAlphanumeric(10);
            case "\\d{6}" -> generateNumbers(6);
            case "\\d{4}" -> generateNumbers(4);
            case "[A-Z]{3}[0-9]{3}" -> generateLetters(3) + generateNumbers(3);
            case "[A-Z0-9]{8}" -> generateAlphanumeric(8);
            
            default -> {
                // Try to parse and generate from common regex patterns
                if (regex.contains("\\d{") && regex.contains("}")) {
                    yield handleDigitPattern(regex);
                } else if (regex.contains("[A-Z]") && regex.contains("{") && regex.contains("}")) {
                    yield handleLetterPattern(regex);
                } else if (regex.contains("https?:\\/\\/") || regex.contains("www\\.")) {
                    yield generateWebsiteUrl();
                } else if (regex.contains("@") && regex.contains("\\.")) {
                    yield generateEmail();
                } else {
                    // Fallback: try to extract patterns
                    yield generateFromComplexRegex(regex);
                }
            }
        };
    }
    
    private String handleDigitPattern(String regex) {
        // Extract number from patterns like \\d{3} or \\d{2,4}
        Pattern digitPattern = Pattern.compile("\\\\d\\{(\\d+)(?:,(\\d+))?\\}");
        Matcher matcher = digitPattern.matcher(regex);
        
        if (matcher.find()) {
            int min = Integer.parseInt(matcher.group(1));
            int max = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : min;
            int length = min == max ? min : random.nextInt(max - min + 1) + min;
            return generateNumbers(length);
        }
        
        return generateNumbers(4); // fallback
    }
    
    private String handleLetterPattern(String regex) {
        // Handle patterns like [A-Z]{3} or [a-z]{2,5}
        Pattern letterPattern = Pattern.compile("\\[A-Za-z\\]\\{(\\d+)(?:,(\\d+))?\\}");
        Matcher matcher = letterPattern.matcher(regex);
        
        if (matcher.find()) {
            int min = Integer.parseInt(matcher.group(1));
            int max = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : min;
            int length = min == max ? min : random.nextInt(max - min + 1) + min;
            return generateLetters(length);
        }
        
        return generateLetters(3); // fallback
    }
    
    private String generateFromComplexRegex(String regex) {
        // Simple fallback for complex patterns
        if (regex.length() > 20) {
            return generateAlphanumeric(8);
        }
        return generateRandomString(6);
    }
    
    private String generateWebsiteUrl() {
        String[] domains = {"example.com", "test.org", "demo.net", "sample.io", "mysite.co"};
        String[] protocols = {"https://", "http://", ""};
        String[] prefixes = {"www.", ""};
        
        return protocols[random.nextInt(protocols.length)] + 
               prefixes[random.nextInt(prefixes.length)] + 
               domains[random.nextInt(domains.length)];
    }
    
    private String generateEmail() {
        String[] domains = {"gmail.com", "yahoo.com", "company.com", "example.org"};
        return generateRandomString(5).toLowerCase() + "@" + domains[random.nextInt(domains.length)];
    }
    
    private String generateCapitalizedWord() {
        String[] cities = {"New York", "London", "Paris", "Tokyo", "Sydney", "Berlin", "Toronto", "Mumbai"};
        return cities[random.nextInt(cities.length)];
    }
    
    private String resolvePlaceholder(String placeholder, DynamicFieldContext context) {
        return switch (placeholder.toLowerCase()) {
            // Simple generators
            case "6digitcode", "6digit" -> generateNumbers(6);
            case "randomstring" -> generateRandomString(8);
            case "timestamp" -> String.valueOf(System.currentTimeMillis());
            case "datetime" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "date" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "uuid" -> UUID.randomUUID().toString();
            case "shortuuid" -> UUID.randomUUID().toString().substring(0, 8);
            
            default -> {
                // Pattern-based generators
                if (placeholder.matches("\\d+\\s*random[- ]?numbers?")) {
                    int count = extractNumber(placeholder);
                    yield generateNumbers(count);
                } else if (placeholder.matches("\\d+\\s*random[- ]?letters?")) {
                    int count = extractNumber(placeholder);
                    yield generateLetters(count);
                } else if (placeholder.matches("\\d+\\s*random[- ]?chars?")) {
                    int count = extractNumber(placeholder);
                    yield generateAlphanumeric(count);
                }
                // Request body fields
                else if (placeholder.startsWith("requestbody.") || placeholder.startsWith("reqbody.")) {
                    String fieldPath = placeholder.substring(placeholder.indexOf('.') + 1);
                    yield getValueFromRequestBody(fieldPath, context);
                }
                // Request parameters  
                else if (placeholder.startsWith("query.") || placeholder.startsWith("param.")) {
                    String paramName = placeholder.substring(placeholder.indexOf('.') + 1);
                    yield context.getQueryParams().getOrDefault(paramName, "");
                }
                // Path parameters
                else if (placeholder.startsWith("path.")) {
                    String paramName = placeholder.substring(5);
                    yield context.getPathParams().getOrDefault(paramName, "");
                }
                // Headers
                else if (placeholder.startsWith("header.")) {
                    String headerName = placeholder.substring(7);
                    yield context.getHeaders().getOrDefault(headerName, "");
                }
                // Combinations (handle + operator)
                else if (placeholder.contains("+")) {
                    yield processCombination(placeholder, context);
                }
                // Unknown placeholder
                else {
                    log.warn("Unknown placeholder: {}", placeholder);
                    yield placeholder;
                }
            }
        };
    }
    
    private String processCombination(String combination, DynamicFieldContext context) {
        StringBuilder result = new StringBuilder();
        String[] parts = combination.split("\\+");
        
        for (String part : parts) {
            part = part.trim();
            String value = resolvePlaceholder(part, context);
            result.append(value);
        }
        
        return result.toString();
    }
    
    private String getValueFromRequestBody(String fieldPath, DynamicFieldContext context) {
        if (context.getRequestBody() == null) {
            return "";
        }
        
        if (context.getRequestBody() instanceof Map) {
            Map<String, Object> body = (Map<String, Object>) context.getRequestBody();
            Object value = body.get(fieldPath);
            return value != null ? String.valueOf(value) : "";
        }
        
        return "";
    }
    
    private int extractNumber(String text) {
        Pattern numberPattern = Pattern.compile("(\\d+)");
        Matcher matcher = numberPattern.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }
    
    // Helper generator methods
    private String generateNumbers(int length) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(random.nextInt(10));
        }
        return result.toString();
    }
    
    private String generateLetters(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }
    
    private String generateAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }
    
    private String generateRandomString(int length) {
        return generateAlphanumeric(length);
    }
}

// ========================================
// 4. UPDATED TEMPLATE PROCESSOR SERVICE
// ========================================

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
    
    private Map<String, String> currentGeneratedValues = new HashMap<>();
    
    public Object processTemplate(Object template, TemplateContext context) {
        currentGeneratedValues.clear();
        
        // Only process regular templates ({{variable_name}})
        // Flexible patterns will be handled by FlexibleDynamicFieldProcessor
        Object result = processTemplateInternal(template, context);
        
        return result;
    }
    
    private Object processTemplateInternal(Object template, TemplateContext context) {
        if (template == null) return null;
        
        if (template instanceof String) {
            return processStringTemplate((String) template, context);
        } else if (template instanceof Map) {
            return processMapTemplate((Map<String, Object>) template, context);
        } else if (template instanceof List) {
            return processListTemplate((List<Object>) template, context);
        }
        
        return template;
    }
    
    // Handle ONLY {{template_variable}} patterns
    private Object processStringTemplate(String template, TemplateContext context) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        
        // Check if the entire string is just one template variable
        Matcher singleVarMatcher = Pattern.compile("^\\{\\{([^}]+)\\}\\}$").matcher(template);
        if (singleVarMatcher.matches()) {
            String variableName = singleVarMatcher.group(1).trim();
            Object resolved = resolveVariable(variableName, context);
            
            // If it's an object/list/number/boolean, return it directly (not as string)
            if (resolved instanceof Map || resolved instanceof List || 
                resolved instanceof Number || resolved instanceof Boolean) {
                return resolved;
            }
            
            return String.valueOf(resolved);
        }
        
        // For templates with multiple variables or mixed content, process as string
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            Object resolved = resolveVariable(variableName, context);
            String replacement = String.valueOf(resolved);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    private Object resolveVariable(String variableName, TemplateContext context) {
        return switch (variableName) {
            // Generate and store these specific values
            case "created_record_guid", "generated_guid" -> {
                String guid = UUID.randomUUID().toString();
                currentGeneratedValues.put(variableName, guid);
                yield guid;
            }
            case "generated_6_digit_id" -> {
                String sixDigitId = String.format("%06d", new Random().nextInt(999999));
                currentGeneratedValues.put(variableName, sixDigitId);
                yield sixDigitId;
            }
            
            // Timestamps (strings)
            case "current_timestamp" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "current_date" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "timestamp" -> String.valueOf(System.currentTimeMillis());
            
            // Return actual objects, not serialized strings
            case "created_object_with_extras" -> context.getCreatedObject();
            case "filtered_results" -> context.getFilteredResults();
            case "paginated_results" -> context.getPaginatedResults();
            case "paginated_filtered_results" -> context.getPaginatedFilteredResults();
            case "search_results" -> context.getSearchResults();
            case "found_user" -> context.getFoundObject();
            case "updated_profile" -> context.getUpdatedObject();
            case "request_body" -> context.getRequestBody();
            
            // Numbers and booleans
            case "total_count" -> context.getTotalCount();
            case "returned_count" -> context.getReturnedCount();
            case "has_more_results" -> context.isHasMoreResults();
            case "has_next_page" -> context.isHasNextPage();
            case "has_previous_page" -> context.isHasPreviousPage();
            
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
                    yield "{{" + variableName + "}}";
                }
            }
        };
    }
    
    private Map<String, Object> processMapTemplate(Map<String, Object> template, TemplateContext context) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            String processedKey = String.valueOf(processStringTemplate(key, context));
            Object processedValue = processTemplateInternal(value, context);
            
            result.put(processedKey, processedValue);
        }
        
        return result;
    }
    
    private List<Object> processListTemplate(List<Object> template, TemplateContext context) {
        List<Object> result = new ArrayList<>();
        
        for (Object item : template) {
            result.add(processTemplateInternal(item, context));
        }
        
        return result;
    }
    
    public Map<String, String> getGeneratedValues() {
        return new HashMap<>(currentGeneratedValues);
    }
    
    public void clearGeneratedValues() {
        currentGeneratedValues.clear();
    }
}

// ========================================
// 5. UPDATED ENHANCED DYNAMIC ROUTE SERVICE
// ========================================

package com.mockapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedDynamicRouteService {
    
    private final FileStorageService fileStorage;
    private final TemplateProcessorService templateProcessor;
    private final FlexibleDynamicFieldProcessor flexibleProcessor;
    private final NestedFieldProcessor nestedFieldProcessor;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    private static final String ROUTES_COLLECTION = "_dynamic_routes";
    
    // ... existing route management methods unchanged (registerRoute, updateRoute, etc.) ...
    
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
    
    // Helper methods
    private Map<String, String> convertHttpHeadersToMap(HttpHeaders headers) {
        Map<String, String> headerMap = new HashMap<>();
        headers.forEach((key, values) -> {
            if (!values.isEmpty()) {
                headerMap.put(key, values.get(0));
            }
        });
        return headerMap;
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
    
    // Helper methods for route management
    public List<RouteConfig> getActiveRoutes() {
        return getAllRoutes().stream()
                .filter(RouteConfig::isEnabled)
                .collect(Collectors.toList());
    }
    
    public List<RouteConfig> getAllRoutes() {
        List<String> routeIds = fileStorage.getItemIdsFromCollection(ROUTES_COLLECTION);
        return routeIds.stream()
                .map(this::getRoute)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    public RouteConfig getRoute(String routeId) {
        return fileStorage.readItemFromCollection(ROUTES_COLLECTION, routeId, RouteConfig.class);
    }
    
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
// 6. ENHANCED DYNAMIC ENDPOINT CONTROLLER
// ========================================

package com.mockapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockapi.exception.MockApiException;
import com.mockapi.model.ApiResponse;
import com.mockapi.model.RouteConfig;
import com.mockapi.service.EnhancedDynamicRouteService;
import com.mockapi.service.FileStorageService;
import com.mockapi.service.FlexibleDynamicFieldProcessor;
import com.mockapi.service.TemplateProcessorService;
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
    
    // HTTP METHOD HANDLERS
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
    
    // HELPER METHODS
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
// USAGE EXAMPLE CONFIGURATION
// ========================================

/*
Complete Example Route Configuration:

{
  "routeId": "post_comprehensive_example",
  "description": "POST endpoint with flexible dynamic fields and saving",
  "requestConfig": {
    "httpMethod": "POST",
    "urlPattern": "/api/contacts",
    "collection": "contacts",
    "storageStrategy": "collection_file",
    "requiredBodyParams": ["firstName", "lastName", "email"],
    "optionalBodyParams": ["phone", "company"],
    "fieldRules": [
      {
        "fieldName": "email",
        "regexPattern": "^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$",
        "statusCode": 422,
        "message": "Invalid email format",
        "responseBody": {
          "error": "INVALID_EMAIL",
          "field": "email"
        }
      }
    ]
  },
  "responseConfig": {
    "statusCode": 201,
    "responseBody": {
      "success": true,
      "contactId": "\\d{6}",                          // Regex pattern -> "123456"
      "trackingCode": "TRK-{8 random chars}",         // Curly brace pattern -> "TRK-A3B9K2M5"
      "userAgent": "{header.user-agent}",             // Header value
      "timestamp": "{datetime}",                      // Current datetime
      "nested": {
        "referenceId": "[A-Z]{3}[0-9]{4}",           // Complex regex -> "ABC1234"
        "sessionId": "{uuid}",                       // UUID generator
        "requestData": {
          "userName": "{requestbody.firstName}",     // Request body field
          "category": "{query.category}",            // Query parameter
          "processId": "\\d{4}-\\d{4}-\\d{4}"       // Multi-part pattern -> "1234-5678-9012"
        }
      }
    },
    "additionalFields": {
      "serverTimestamp": "{{current_timestamp}}",    // Regular template variable
      "generatedId": "{{generated_guid}}"            // Regular template variable
    },
    "customHeaders": {
      "X-Contact-ID": "\\d{6}",                     // Generated header
      "X-Tracking": "TRK-{6 random chars}"          // Generated header
    },
    "delayMs": 200
  }
}




