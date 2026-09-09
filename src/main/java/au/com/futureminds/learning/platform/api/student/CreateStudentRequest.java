package au.com.futureminds.learning.platform.api.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deliberately excludes any parent/owner identity field - ownership is
 * resolved solely from the authenticated JWT subject, never from the
 * request body.
 */
public record CreateStudentRequest(

        @NotBlank(message = "firstName must not be blank")
        @Size(max = 100, message = "firstName must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "schoolYear must not be blank")
        @Size(max = 50, message = "schoolYear must not exceed 50 characters")
        String schoolYear,

        @NotBlank(message = "preparationGoal must not be blank")
        @Size(max = 50, message = "preparationGoal must not exceed 50 characters")
        String preparationGoal

) {
}
