package egain.oassdk.core.validator;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.ValidationException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validator for OpenAPI specifications
 */
public class OASValidator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");

    /**
     * Validate OpenAPI specification
     *
     * @param spec Parsed OpenAPI specification
     * @throws ValidationException if validation fails
     */
    public void validate(Map<String, Object> spec) throws ValidationException {
        List<String> errors = new ArrayList<>();

        // Basic structure validation
        validateBasicStructure(spec, errors);

        // Info section validation
        validateInfoSection(spec, errors);

        // Paths validation
        validatePaths(spec, errors);

        // Components validation
        validateComponents(spec, errors);

        // Security validation
        validateSecurity(spec, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException("OpenAPI validation failed:\n" + String.join("\n", errors));
        }
    }

    /**
     * Validate basic OpenAPI structure
     */
    private void validateBasicStructure(Map<String, Object> spec, List<String> errors) {
        if (!spec.containsKey("openapi") && !spec.containsKey("swagger")) {
            errors.add("Missing 'openapi' or 'swagger' field");
        }

        if (!spec.containsKey("info")) {
            errors.add("Missing required 'info' section");
        }

        if (!spec.containsKey("paths")) {
            errors.add("Missing required 'paths' section");
        }
    }

    /**
     * Validate info section
     */
    private void validateInfoSection(Map<String, Object> spec, List<String> errors) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) return;

        // Required fields
        if (!info.containsKey("title")) {
            errors.add("Info section missing required 'title' field");
        }

        if (!info.containsKey("version")) {
            errors.add("Info section missing required 'version' field");
        }

        // Validate version format when present and non-blank (blank is allowed)
        if (info.containsKey("version")) {
            String version = (String) info.get("version");
            if (version != null && !version.isBlank() && !VERSION_PATTERN.matcher(version).matches()) {
                errors.add("Invalid version format in info section: " + version);
            }
        }

        // Validate contact information
        if (info.containsKey("contact")) {
            validateContact(Util.asStringObjectMap(info.get("contact")), errors, "info.contact");
        }

        // Validate license
        if (info.containsKey("license")) {
            validateLicense(Util.asStringObjectMap(info.get("license")), errors, "info.license");
        }
    }

    /**
     * Validate contact information
     */
    private void validateContact(Map<String, Object> contact, List<String> errors, String path) {
        if (contact == null) return;

        if (contact.containsKey("email")) {
            String email = (String) contact.get("email");
            if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
                errors.add("Invalid email format in " + path + ": " + email);
            }
        }

        if (contact.containsKey("url")) {
            String url = (String) contact.get("url");
            if (url != null && !URL_PATTERN.matcher(url).matches()) {
                errors.add("Invalid URL format in " + path + ": " + url);
            }
        }
    }

    /**
     * Validate license information
     */
    private void validateLicense(Map<String, Object> license, List<String> errors, String path) {
        if (license == null) return;

        if (!license.containsKey("name")) {
            errors.add("License section missing required 'name' field in " + path);
        }

        if (license.containsKey("url")) {
            String url = (String) license.get("url");
            if (url != null && !URL_PATTERN.matcher(url).matches()) {
                errors.add("Invalid URL format in " + path + ": " + url);
            }
        }
    }

    /**
     * Validate paths section
     */
    private void validatePaths(Map<String, Object> spec, List<String> errors) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            validatePathItem(path, pathItem, errors);
        }
    }

    /**
     * Validate individual path item
     */
    private void validatePathItem(String path, Map<String, Object> pathItem, List<String> errors) {
        if (pathItem == null) return;

        // Validate HTTP methods
        String[] validMethods = {"get", "post", "put", "delete", "patch", "head", "options", "trace"};
        for (String method : validMethods) {
            if (pathItem.containsKey(method)) {
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                validateOperation(path, method, operation, errors);
            }
        }

        // Validate parameters
        if (pathItem.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(pathItem.get("parameters"));
            validateParameters(parameters, errors, path);
        }
    }

    /**
     * Validate operation
     */
    private void validateOperation(String path, String method, Map<String, Object> operation, List<String> errors) {
        if (operation == null) return;

        // Validate operation ID
        if (operation.containsKey("operationId")) {
            String operationId = (String) operation.get("operationId");
            if (operationId != null && !isValidOperationId(operationId)) {
                errors.add("Invalid operationId in " + method.toUpperCase(Locale.ROOT) + " " + path + ": " + operationId);
            }
        }

        // Validate responses
        if (!operation.containsKey("responses")) {
            errors.add("Operation " + method.toUpperCase(Locale.ROOT) + " " + path + " missing required 'responses' section");
        } else {
            Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
            validateResponses(responses, errors, path, method);
        }

        // Validate parameters
        if (operation.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
            validateParameters(parameters, errors, path + "." + method);
        }
    }

    /**
     * Validate responses
     */
    private void validateResponses(Map<String, Object> responses, List<String> errors, String path, String method) {
        if (responses == null) return;

        // Check for at least one response
        if (responses.isEmpty()) {
            errors.add("Operation " + method.toUpperCase(Locale.ROOT) + " " + path + " has no responses defined");
        }

        // Validate response codes
        for (String responseCode : responses.keySet()) {
            if (!isValidResponseCode(responseCode)) {
                errors.add("Invalid response code in " + method.toUpperCase(Locale.ROOT) + " " + path + ": " + responseCode);
            }
        }
    }

    /**
     * Validate parameters
     */
    private void validateParameters(List<Map<String, Object>> parameters, List<String> errors, String path) {
        if (parameters == null) return;

        for (int i = 0; i < parameters.size(); i++) {
            Map<String, Object> param = parameters.get(i);
            if (param == null) continue;

            // Skip validation for $ref parameters - they will be validated when resolved
            if (param.containsKey("$ref")) {
                continue;
            }

            // Required fields
            if (!param.containsKey("name")) {
                errors.add("Parameter " + i + " in " + path + " missing required 'name' field");
            }

            if (!param.containsKey("in")) {
                errors.add("Parameter " + i + " in " + path + " missing required 'in' field");
            } else {
                String in = (String) param.get("in");
                if (!Arrays.asList("query", "header", "path", "cookie").contains(in)) {
                    errors.add("Invalid parameter location in " + path + ": " + in);
                }
            }
        }
    }

    /**
     * Validate components section
     */
    private void validateComponents(Map<String, Object> spec, List<String> errors) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return;

        // Validate schemas
        if (components.containsKey("schemas")) {
            Map<String, Object> schemas = Util.asStringObjectMap(components.get("schemas"));
            validateSchemas(schemas, errors);
        }

        // Validate security schemes
        if (components.containsKey("securitySchemes")) {
            Map<String, Object> securitySchemes = Util.asStringObjectMap(components.get("securitySchemes"));
            validateSecuritySchemes(securitySchemes, errors);
        }
    }

    /**
     * Validate schemas
     */
    private void validateSchemas(Map<String, Object> schemas, List<String> errors) {
        if (schemas == null) return;

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            Map<String, Object> schema = Util.asStringObjectMap(schemaEntry.getValue());

            if (schema == null) continue;

            // Validate schema name
            if (!isValidSchemaName(schemaName)) {
                errors.add("Invalid schema name: " + schemaName);
            }
        }
    }

    /**
     * Validate security schemes
     */
    private void validateSecuritySchemes(Map<String, Object> securitySchemes, List<String> errors) {
        if (securitySchemes == null) return;

        for (Map.Entry<String, Object> schemeEntry : securitySchemes.entrySet()) {
            String schemeName = schemeEntry.getKey();
            Map<String, Object> scheme = Util.asStringObjectMap(schemeEntry.getValue());

            if (scheme == null) continue;

            if (!scheme.containsKey("type")) {
                errors.add("Security scheme " + schemeName + " missing required 'type' field");
            } else {
                String type = (String) scheme.get("type");
                if (!Arrays.asList("apiKey", "http", "oauth2", "openIdConnect").contains(type)) {
                    errors.add("Invalid security scheme type in " + schemeName + ": " + type);
                }
            }
        }
    }

    /**
     * Validate security section
     */
    private void validateSecurity(Map<String, Object> spec, List<String> errors) {
        if (!spec.containsKey("security")) return;

        List<Map<String, Object>> security = Util.asStringObjectMapList(spec.get("security"));
        if (security == null) return;

        for (int i = 0; i < security.size(); i++) {
            Map<String, Object> securityRequirement = security.get(i);
            if (securityRequirement == null) continue;

            // Each security requirement should reference a defined security scheme
            for (String schemeName : securityRequirement.keySet()) {
                if (!isSecuritySchemeDefined(spec, schemeName)) {
                    errors.add("Security requirement " + i + " references undefined security scheme: " + schemeName);
                }
            }
        }
    }

    /**
     * Check if security scheme is defined
     */
    private boolean isSecuritySchemeDefined(Map<String, Object> spec, String schemeName) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        if (components == null) return false;

        Map<String, Object> securitySchemes = Util.asStringObjectMap(components.get("securitySchemes"));
        if (securitySchemes == null) return false;

        return securitySchemes.containsKey(schemeName);
    }

    /**
     * Validate operation ID format
     */
    private boolean isValidOperationId(String operationId) {
        return operationId.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
    }

    /**
     * Validate response code format
     */
    private boolean isValidResponseCode(String responseCode) {
        if ("default".equals(responseCode)) return true;

        try {
            int code = Integer.parseInt(responseCode);
            return code >= 100 && code <= 599;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validate schema name format
     */
    private boolean isValidSchemaName(String schemaName) {
        return schemaName.matches("^[a-zA-Z][a-zA-Z0-9_-]*$");
    }
}
