package au.com.futureminds.learning.platform.security;

import au.com.futureminds.learning.platform.api.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Assumption: no local-development CORS origin convention exists yet for the
 * future-minds-mobile client, so the standard Expo/Metro dev-server origin is
 * used as the smallest reasonable placeholder. Revisit per-environment.
 */
@Configuration
public class SecurityConfig {

    private static final String LOCAL_MOBILE_DEV_ORIGIN = "http://localhost:8081";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KeycloakRealmRoleConverter keycloakRealmRoleConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, ApiPaths.V1 + "/system/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.V1+"/system/protected/parent").hasRole("PARENT")
                        .requestMatchers(HttpMethod.GET, ApiPaths.V1 + "/parents/me").hasRole("PARENT")
                        .requestMatchers(HttpMethod.PUT, ApiPaths.V1 + "/parents/me").hasRole("PARENT")
                        .requestMatchers(HttpMethod.PATCH, ApiPaths.V1 + "/parents/me").hasRole("PARENT")
                        .requestMatchers(HttpMethod.POST, ApiPaths.V1 + "/parents/me/consents").hasRole("PARENT")
                        .requestMatchers(HttpMethod.GET, ApiPaths.V1 + "/parents/me/consents").hasRole("PARENT")
                        .anyRequest().authenticated())
                // Configure this application as an OAuth2 Resource Server.
                // Spring Security will validate incoming Bearer JWTs using
                // spring.security.oauth2.resourceserver.jwt.issuer-uri.
//                .oauth2ResourceServer(oauth2 ->
//                        oauth2.jwt(Customizer.withDefaults())
//                )
                //with hasRole
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter(
                                                keycloakRealmRoleConverter
                                        )
                                )
                        )
                )
                // Missing or invalid authentication returns HTTP 401.
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(LOCAL_MOBILE_DEV_ORIGIN));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            KeycloakRealmRoleConverter keycloakRealmRoleConverter) {

        JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter jwtAuthenticationConverter =
                new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Collection<GrantedAuthority> scopeAuthorities =
                    scopeAuthoritiesConverter.convert(jwt);

            Collection<GrantedAuthority> realmRoleAuthorities =
                    keycloakRealmRoleConverter.convert(jwt);

            List<GrantedAuthority> authorities = new ArrayList<>();

            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            if (realmRoleAuthorities != null) {
                authorities.addAll(realmRoleAuthorities);
            }

            return authorities;
        });

        return jwtAuthenticationConverter;
    }
}
