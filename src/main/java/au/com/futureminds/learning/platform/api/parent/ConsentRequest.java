package au.com.futureminds.learning.platform.api.parent;

import jakarta.validation.constraints.NotBlank;

/**
 * Deliberately excludes any identity/ownership field - the parent is
 * resolved solely from the authenticated JWT subject, never from the
 * request body.
 */
public record ConsentRequest(

        @NotBlank(message = "consentType must not be blank")
        String consentType,

        @NotBlank(message = "consentVersion must not be blank")
        String consentVersion

) {
}
