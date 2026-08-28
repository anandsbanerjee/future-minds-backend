package au.com.futureminds.learning.platform.api.error;

public record FieldValidationError(
        String field,
        String message
) {
}
