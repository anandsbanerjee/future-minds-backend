package au.com.futureminds.learning.platform.api.parent;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccount;
import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParentControllerTest {

    private static final String PUT_ME_URI = "/api/v1/parents/me";
    private static final String GET_ME_URI = "/api/v1/parents/me";
    private static final String SUBJECT = "keycloak-subject-abc";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParentAccountService parentAccountService;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(put(PUT_ME_URI))
                .andExpect(status().isUnauthorized());

        verify(parentAccountService, never()).provision(any(), any(), any(), any());
    }

    @Test
    void authenticatedNonParentRequestIsForbidden() throws Exception {
        mockMvc.perform(put(PUT_ME_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT).claim("email", "someone@example.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        verify(parentAccountService, never()).provision(any(), any(), any(), any());
    }

    @Test
    void firstAuthenticatedCallCreatesAndReturnsTheParentAccount() throws Exception {
        ParentAccount created = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.provision(eq(SUBJECT), eq("parent@example.com"), eq("Ada"), eq("Lovelace")))
                .thenReturn(new ParentAccountService.ProvisionResult(created, true));

        mockMvc.perform(put(PUT_ME_URI).with(jwt()
                        .jwt(builder -> builder
                                .subject(SUBJECT)
                                .claim("email", "parent@example.com")
                                .claim("given_name", "Ada")
                                .claim("family_name", "Lovelace"))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("parent@example.com"))
                .andExpect(jsonPath("$.givenName").value("Ada"))
                .andExpect(jsonPath("$.familyName").value("Lovelace"));
    }

    @Test
    void repeatedAuthenticatedCallIsIdempotentAndDoesNotReportCreation() throws Exception {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.provision(eq(SUBJECT), any(), any(), any()))
                .thenReturn(new ParentAccountService.ProvisionResult(existing, false));

        mockMvc.perform(put(PUT_ME_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT).claim("email", "parent@example.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("parent@example.com"));
    }

    @Test
    void requestCannotSpoofAnotherIdentityViaTheRequestBody() throws Exception {
        ParentAccount created = new ParentAccount(SUBJECT, "real-owner@example.com", null, null);
        when(parentAccountService.provision(eq(SUBJECT), eq("real-owner@example.com"), any(), any()))
                .thenReturn(new ParentAccountService.ProvisionResult(created, true));

        mockMvc.perform(put(PUT_ME_URI)
                        .with(jwt()
                                .jwt(builder -> builder.subject(SUBJECT).claim("email", "real-owner@example.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sub": "someone-elses-subject",
                                  "email": "attacker@example.com"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(parentAccountService).provision(subjectCaptor.capture(), emailCaptor.capture(), any(), any());

        assertThat(subjectCaptor.getValue()).isEqualTo(SUBJECT);
        assertThat(emailCaptor.getValue()).isEqualTo("real-owner@example.com");
    }

    @Test
    void responseDoesNotExposeInternalOrSecuritySensitiveFields() throws Exception {
        ParentAccount created = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.provision(eq(SUBJECT), any(), any(), any()))
                .thenReturn(new ParentAccountService.ProvisionResult(created, true));

        mockMvc.perform(put(PUT_ME_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT).claim("email", "parent@example.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.externalSubject").doesNotExist())
                .andExpect(jsonPath("$.sub").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void getMeUnauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get(GET_ME_URI))
                .andExpect(status().isUnauthorized());

        verify(parentAccountService, never()).findByExternalSubject(any());
        verify(parentAccountService, never()).provision(any(), any(), any(), any());
    }

    @Test
    void getMeAuthenticatedNonParentRequestIsForbidden() throws Exception {
        mockMvc.perform(get(GET_ME_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT).claim("email", "someone@example.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        verify(parentAccountService, never()).findByExternalSubject(any());
        verify(parentAccountService, never()).provision(any(), any(), any(), any());
    }

    @Test
    void getMeReturnsTheExistingParentAccountForTheAuthenticatedSubject() throws Exception {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        mockMvc.perform(get(GET_ME_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("parent@example.com"))
                .andExpect(jsonPath("$.givenName").value("Ada"))
                .andExpect(jsonPath("$.familyName").value("Lovelace"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.externalSubject").doesNotExist());

        verify(parentAccountService, never()).provision(any(), any(), any(), any());
    }

    @Test
    void getMeReturnsNotFoundWhenNoParentAccountExistsForTheAuthenticatedSubject() throws Exception {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        mockMvc.perform(get(GET_ME_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isNotFound());

        verify(parentAccountService, never()).provision(any(), any(), any(), any());
    }

    @Test
    void getMeDerivesIdentityOnlyFromTheJwtSubject() throws Exception {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        mockMvc.perform(get(GET_ME_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(parentAccountService).findByExternalSubject(subjectCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(SUBJECT);
    }
}
