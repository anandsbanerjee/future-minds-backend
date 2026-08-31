package au.com.futureminds.learning.platform.api.parent;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccount;

import java.time.LocalDateTime;

public record ParentAccountResponse(
        String email,
        String givenName,
        String familyName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ParentAccountResponse from(ParentAccount account) {
        return new ParentAccountResponse(
                account.getEmail(),
                account.getGivenName(),
                account.getFamilyName(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
