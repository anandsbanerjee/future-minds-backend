package au.com.futureminds.learning.platform.security;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // The "test" profile excludes DataSource/JPA autoconfiguration; ParentAccountService
    // needs a real ParentAccountRepository, so it is mocked out here rather than constructed.
    @MockitoBean
    private ParentAccountService parentAccountService;

    @Test
    void systemStatusIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void applicationEndpointIsProtectedByDefault() throws Exception {
        mockMvc.perform(post("/api/v1/system/validation-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "test"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordEncoderEncodesAndMatchesRawPassword() {
        String rawPassword = "correct-horse-battery-staple";

        String encoded = passwordEncoder.encode(rawPassword);

        assertThat(encoded).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
    }
}
