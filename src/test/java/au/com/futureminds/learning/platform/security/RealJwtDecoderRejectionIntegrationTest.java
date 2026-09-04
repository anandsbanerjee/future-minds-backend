package au.com.futureminds.learning.platform.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the REAL Spring Security JWT pipeline (NimbusJwtDecoder + the
 * default Spring-Security-provided issuer/timestamp validator chain,
 * org.springframework.security.oauth2.jwt.JwtValidators#createDefaultWithIssuer)
 * against representative invalid bearer tokens.
 *
 * SecurityMockMvcRequestPostProcessors.jwt(), used elsewhere in this project's
 * tests, injects an already-authenticated SecurityContext and never invokes a
 * JwtDecoder - it cannot prove that decoding/validation itself rejects bad
 * tokens. This class instead sends a real Authorization header through the
 * ordinary filter chain, backed by a JwtDecoder built from a locally
 * generated RSA key pair and a fixed issuer string. This avoids any network
 * call to a real/mocked Keycloak (no JWKS endpoint, no OIDC discovery) while
 * still exercising the identical decoder/validator classes production uses -
 * only the *source* of the signing key and issuer is swapped from
 * "fetched from Keycloak" to "supplied locally in the test".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "AUTH_ISSUER_URI=" + RealJwtDecoderRejectionIntegrationTest.ISSUER)
class RealJwtDecoderRejectionIntegrationTest {

    static final String ISSUER = "https://test-issuer.example/realms/future-minds";

    private static final String PROTECTED_PARENT_URI = "/api/v1/system/protected/parent";

    // Key trusted by the JwtDecoder under test (see RealDecoderConfig below).
    private static final RSAKey SIGNING_KEY = generateKey();
    // A different key, never trusted by the decoder - used to produce a
    // structurally valid but incorrectly-signed token.
    private static final RSAKey UNTRUSTED_KEY = generateKey();

    @Autowired
    private MockMvc mockMvc;

    // Full application context boots ParentController regardless of which
    // endpoints this test exercises; the "test" profile excludes DataSource/JPA
    // autoconfiguration, so ParentAccountService's repository dependency must
    // be mocked out here too (same reasoning as the existing security tests).
    @MockitoBean
    private ParentAccountService parentAccountService;

    @TestConfiguration
    static class RealDecoderConfig {

        @Bean
        JwtDecoder jwtDecoder() throws JOSEException {
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey((RSAPublicKey) SIGNING_KEY.toRSAPublicKey())
                    .build();
            // Same default validator Spring Boot's own issuer-uri autoconfiguration
            // applies in production (issuer check + exp/nbf timestamp check).
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }

    @Test
    void validTokenThroughTheRealDecoderIsAccepted() throws Exception {
        String token = token(SIGNING_KEY, ISSUER, Instant.now().minusSeconds(30), Instant.now().plusSeconds(300));

        mockMvc.perform(get(PROTECTED_PARENT_URI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void malformedBearerTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get(PROTECTED_PARENT_URI)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt-string"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejectedWith401() throws Exception {
        String token = token(SIGNING_KEY, ISSUER, Instant.now().minusSeconds(600), Instant.now().minusSeconds(60));

        mockMvc.perform(get(PROTECTED_PARENT_URI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithInvalidSignatureIsRejectedWith401() throws Exception {
        // Well-formed and unexpired, but signed with a key the decoder does not trust.
        String token = token(UNTRUSTED_KEY, ISSUER, Instant.now().minusSeconds(30), Instant.now().plusSeconds(300));

        mockMvc.perform(get(PROTECTED_PARENT_URI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithWrongIssuerIsRejectedWith401() throws Exception {
        String token = token(SIGNING_KEY, "https://wrong-issuer.example/realms/other",
                Instant.now().minusSeconds(30), Instant.now().plusSeconds(300));

        mockMvc.perform(get(PROTECTED_PARENT_URI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static String token(RSAKey signingKey, String issuer, Instant issuedAt, Instant expiry) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("keycloak-subject-real-decoder-test")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiry))
                .claim("realm_access", Map.of("roles", List.of("PARENT")))
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims);
        signedJwt.sign(new RSASSASigner(signingKey));
        return signedJwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key-" + Instant.now().toEpochMilli()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate RSA test key", e);
        }
    }
}
