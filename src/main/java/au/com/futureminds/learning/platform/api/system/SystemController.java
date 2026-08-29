package au.com.futureminds.learning.platform.api.system;

import au.com.futureminds.learning.platform.api.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }
    //public API
    @GetMapping("/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse("UP", applicationName);
    }

    //Checking KeyCloak IDP access-token based access only
    @GetMapping("/protected")
    public SystemStatusResponse statusSecure() {
        return new SystemStatusResponse("SECURE-UP", applicationName);
    }
    //testing with hasRole("PARENT")
    @GetMapping("/protected/parent")
    public SystemStatusResponse statusSecureRole() {
        return new SystemStatusResponse("SECURE-ROLE-UP", applicationName);
    }

    @PostMapping("/validation-check")
    public ResponseEntity<Void> validationCheck(@Valid @RequestBody ValidationCheckRequest request) {
        return ResponseEntity.noContent().build();
    }
}
