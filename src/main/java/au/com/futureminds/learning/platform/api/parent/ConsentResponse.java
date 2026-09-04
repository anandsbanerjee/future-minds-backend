package au.com.futureminds.learning.platform.api.parent;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentConsent;

import java.time.LocalDateTime;

public record ConsentResponse(
        String consentType,
        String consentVersion,
        LocalDateTime recordedAt
) {

    public static ConsentResponse from(ParentConsent consent) {
        return new ConsentResponse(
                consent.getConsentType().name(),
                consent.getConsentVersion(),
                consent.getRecordedAt());
    }
}
