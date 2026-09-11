package au.com.futureminds.learning.platform.api.student;

import au.com.futureminds.learning.platform.api.parent.NullOrNotBlank;
import jakarta.validation.constraints.Size;

/**
 * Partial update: an omitted (null) field is left unchanged. Deliberately
 * excludes id, parentAccountId, createdAt and updatedAt - those are never
 * accepted from a student-update request.
 */
public record UpdateStudentRequest(

        @NullOrNotBlank(message = "firstName must not be blank")
        @Size(max = 100, message = "firstName must not exceed 100 characters")
        String firstName,

        @NullOrNotBlank(message = "schoolYear must not be blank")
        @Size(max = 50, message = "schoolYear must not exceed 50 characters")
        String schoolYear,

        @NullOrNotBlank(message = "preparationGoal must not be blank")
        @Size(max = 50, message = "preparationGoal must not exceed 50 characters")
        String preparationGoal

) {
}
