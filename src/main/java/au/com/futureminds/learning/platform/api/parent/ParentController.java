package au.com.futureminds.learning.platform.api.parent;

import au.com.futureminds.learning.platform.api.ApiPaths;
import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/parents")
public class ParentController {

    private final ParentAccountService parentAccountService;

    public ParentController(ParentAccountService parentAccountService) {
        this.parentAccountService = parentAccountService;
    }

    /**
     * Identity is taken solely from the validated JWT (sub, email) - there is no
     * request body, so a caller cannot supply or spoof another subject/email.
     */
    @PutMapping("/me")
    public ResponseEntity<ParentAccountResponse> putMe(@AuthenticationPrincipal Jwt jwt) {
        ParentAccountService.ProvisionResult result = parentAccountService.provision(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));

        ParentAccountResponse body = ParentAccountResponse.from(result.parentAccount());

        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(body)
                : ResponseEntity.ok(body);
    }
}
