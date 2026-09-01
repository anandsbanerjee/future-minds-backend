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

@Entity
@Table(name = "parent_profile_audit")
public class ParentProfileAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_account_id", nullable = false)
    private Long parentAccountId;

    @Column(name = "change_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ParentProfileChangeType changeType;

    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime changedAt;

    protected ParentProfileAudit() {
    }

    public ParentProfileAudit(Long parentAccountId, ParentProfileChangeType changeType) {
        this.parentAccountId = parentAccountId;
        this.changeType = changeType;
    }

    public Long getId() {
        return id;
    }

    public Long getParentAccountId() {
        return parentAccountId;
    }

    public ParentProfileChangeType getChangeType() {
        return changeType;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
