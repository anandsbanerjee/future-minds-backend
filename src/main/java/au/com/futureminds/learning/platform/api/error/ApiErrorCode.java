package au.com.futureminds.learning.platform.api.error;

public enum ApiErrorCode {

    VALIDATION_FAILED("COMMON-VALIDATION-001"),
    MALFORMED_REQUEST("COMMON-REQUEST-001"),
    INTERNAL_ERROR("COMMON-SERVER-001");

    private final String code;

    ApiErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
