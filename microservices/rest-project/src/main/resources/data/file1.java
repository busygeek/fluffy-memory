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