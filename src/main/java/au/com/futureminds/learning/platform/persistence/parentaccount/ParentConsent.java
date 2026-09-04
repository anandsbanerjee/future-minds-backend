package au.com.futureminds.learning.platform.persistence.parentaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One immutable consent event for a parent. Rows are append-only - there is
 * deliberately no update pathway; a change of consent is recorded as a new row.
 */
@Entity
@Table(name = "parent_consent")
public class ParentConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_account_id", nullable = false)
    private Long parentAccountId;

    @Column(name = "consent_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ParentConsentType consentType;

    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime recordedAt;

    protected ParentConsent() {
    }

    public ParentConsent(Long parentAccountId, ParentConsentType consentType, String consentVersion) {
        this.parentAccountId = parentAccountId;
        this.consentType = consentType;
        this.consentVersion = consentVersion;
    }

    public Long getId() {
        return id;
    }

    public Long getParentAccountId() {
        return parentAccountId;
    }

    public ParentConsentType getConsentType() {
        return consentType;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
