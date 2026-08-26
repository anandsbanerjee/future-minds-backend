package au.com.futureminds.learning.platform.api.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

    private static final String VALIDATION_CHECK_URI = "/api/v1/system/validation-check";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusEndpointReturnsUpWithApplicationName() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("future-minds-backend"));
    }

    @Test
    void validationCheckReturnsNoContentForValidRequest() throws Exception {
        mockMvc.perform(post(VALIDATION_CHECK_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "test"
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void validationCheckReturnsProblemDetailForBlankValue() throws Exception {
        mockMvc.perform(post(VALIDATION_CHECK_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("COMMON-VALIDATION-001"))
                .andExpect(jsonPath("$.instance").value(VALIDATION_CHECK_URI))
                .andExpect(jsonPath("$.errors[?(@.field == 'value')].message")
                        .value("value must not be blank"));
    }

    @Test
    void validationCheckReturnsProblemDetailForMissingValue() throws Exception {
        mockMvc.perform(post(VALIDATION_CHECK_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-VALIDATION-001"))
                .andExpect(jsonPath("$.errors[?(@.field == 'value')]").exists());
    }

    @Test
    void validationCheckReturnsProblemDetailForOversizedValue() throws Exception {
        String oversizedValue = "a".repeat(101);

        mockMvc.perform(post(VALIDATION_CHECK_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "%s"
                                }
                                """.formatted(oversizedValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-VALIDATION-001"))
                .andExpect(jsonPath("$.errors[?(@.field == 'value')].message")
                        .value("value must not exceed 100 characters"));
    }

    @Test
    void validationCheckReturnsProblemDetailForMalformedJson() throws Exception {
        mockMvc.perform(post(VALIDATION_CHECK_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("COMMON-REQUEST-001"))
                .andExpect(jsonPath("$.instance").value(VALIDATION_CHECK_URI))
                .andExpect(jsonPath("$.detail").value("The request body could not be read."));
    }
}
