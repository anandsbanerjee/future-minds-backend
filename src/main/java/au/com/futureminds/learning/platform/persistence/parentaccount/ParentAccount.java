package au.com.futureminds.learning.platform.persistence.parentaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "parent_account")
public class ParentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_subject", nullable = false, unique = true, updatable = false)
    private String externalSubject;

    @Column(name = "email")
    private String email;

    @Column(name = "given_name")
    private String givenName;

    @Column(name = "family_name")
    private String familyName;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ParentAccount() {
    }

    public ParentAccount(String externalSubject, String email, String givenName, String familyName) {
        this.externalSubject = externalSubject;
        this.email = email;
        this.givenName = givenName;
        this.familyName = familyName;
        this.marketingOptIn = false;
    }

    /**
     * Identity-provider synchronisation: email is Keycloak-owned, so every
     * provisioning call may refresh it. givenName/familyName/marketingOptIn are
     * seeded once at creation and then application-owned - see updateGivenName,
     * updateFamilyName, updateMarketingOptIn for parent-driven edits.
     */
    public void syncEmailFromIdentityProvider(String email) {
        this.email = email;
    }

    public boolean updateGivenName(String givenName) {
        if (Objects.equals(this.givenName, givenName)) {
            return false;
        }
        this.givenName = givenName;
        return true;
    }

    public boolean updateFamilyName(String familyName) {
        if (Objects.equals(this.familyName, familyName)) {
            return false;
        }
        this.familyName = familyName;
        return true;
    }

    public boolean updateMarketingOptIn(boolean marketingOptIn) {
        if (this.marketingOptIn == marketingOptIn) {
            return false;
        }
        this.marketingOptIn = marketingOptIn;
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public boolean isMarketingOptIn() {
        return marketingOptIn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
