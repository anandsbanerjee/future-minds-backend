package au.com.futureminds.learning.platform.api.parent;

import jakarta.validation.constraints.Size;

/**
 * Partial update: an omitted (null) field is left unchanged. Deliberately
 * excludes id, externalSubject, email, roles, createdAt and updatedAt - those
 * are never accepted from a profile-update request.
 */
public record ParentProfileUpdateRequest(

        @NullOrNotBlank(message = "givenName must not be blank")
        @Size(max = 100, message = "givenName must not exceed 100 characters")
        String givenName,

        @NullOrNotBlank(message = "familyName must not be blank")
        @Size(max = 100, message = "familyName must not exceed 100 characters")
        String familyName,

        Boolean marketingOptIn

) {
}
