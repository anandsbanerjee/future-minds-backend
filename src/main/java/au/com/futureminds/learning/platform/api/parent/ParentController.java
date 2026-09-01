package au.com.futureminds.learning.platform.api.parent;

import au.com.futureminds.learning.platform.api.ApiPaths;
import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccount;
import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    /**
     * Read-only - looks up the account already provisioned for this JWT subject
     * and never creates or synchronises one.
     */
    @GetMapping("/me")
    public ResponseEntity<ParentAccountResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        ParentAccount account = parentAccountService.findByExternalSubject(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parent account not found."));

        return ResponseEntity.ok(ParentAccountResponse.from(account));
    }

    /**
     * Application-owned profile edit, entirely separate from PUT's
     * identity-provider provisioning. Identity is taken solely from the
     * validated JWT subject; the request body carries only editable fields.
     */
    @PatchMapping("/me")
    public ResponseEntity<ParentAccountResponse> patchMe(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody ParentProfileUpdateRequest request) {
        ParentAccount account = parentAccountService.updateProfile(
                        jwt.getSubject(),
                        request.givenName(),
                        request.familyName(),
                        request.marketingOptIn())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parent account not found."));

        return ResponseEntity.ok(ParentAccountResponse.from(account));
    }
}
