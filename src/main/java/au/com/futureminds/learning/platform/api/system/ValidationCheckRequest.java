package au.com.futureminds.learning.platform.api.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ValidationCheckRequest(

        @NotBlank(message = "value must not be blank")
        @Size(max = 100, message = "value must not exceed 100 characters")
        String value

) {
}
