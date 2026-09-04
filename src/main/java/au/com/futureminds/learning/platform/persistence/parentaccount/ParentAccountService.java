package au.com.futureminds.learning.platform.persistence.parentaccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ParentAccountService {

    private static final Logger log = LoggerFactory.getLogger(ParentAccountService.class);

    private final ParentAccountRepository parentAccountRepository;
    private final ParentProfileAuditRepository parentProfileAuditRepository;
    private final ParentConsentRepository parentConsentRepository;

    public ParentAccountService(ParentAccountRepository parentAccountRepository,
                                 ParentProfileAuditRepository parentProfileAuditRepository,
                                 ParentConsentRepository parentConsentRepository) {
        this.parentAccountRepository = parentAccountRepository;
        this.parentProfileAuditRepository = parentProfileAuditRepository;
        this.parentConsentRepository = parentConsentRepository;
    }

    /**
     * Read-only lookup of an already-provisioned account. Does not create or
     * synchronise anything - callers get an empty Optional if the subject has
     * never called provision(...).
     */
    public Optional<ParentAccount> findByExternalSubject(String externalSubject) {
        return parentAccountRepository.findByExternalSubject(externalSubject);
    }

    /**
     * Idempotent create-or-sync keyed on the immutable Keycloak subject.
     * A unique constraint on external_subject is the authority of last resort:
     * on a concurrent first call from the same subject, the losing transaction
     * falls back to updating the row the winner just inserted, rather than failing.
     *
     * givenName/familyName/marketingOptIn are only seeded on first creation -
     * once a parent account exists it is application-owned for those fields, so
     * repeat provisioning must not clobber a parent's own edits. Only email
     * remains Keycloak-owned and is re-synced on every call.
     */
    @Transactional
    public ProvisionResult provision(String externalSubject, String email, String givenName, String familyName) {
        return parentAccountRepository.findByExternalSubject(externalSubject)
                .map(existing -> syncIdentityProviderEmail(existing, email))
                .orElseGet(() -> createOrRecoverFromRace(externalSubject, email, givenName, familyName));
    }

    /**
     * Application-owned profile edit, entirely separate from identity-provider
     * provisioning/synchronisation. Never creates an account. Only fields that
     * actually change are persisted and audited; omitted (null) fields are left
     * untouched.
     */
    @Transactional
    public Optional<ParentAccount> updateProfile(String externalSubject, String givenName, String familyName, Boolean marketingOptIn) {
        return parentAccountRepository.findByExternalSubject(externalSubject)
                .map(account -> applyProfileUpdate(account, givenName, familyName, marketingOptIn));
    }

    /**
     * Records ONE immutable consent event for an already-provisioned parent.
     * Never creates a ParentAccount, and never updates a prior consent row -
     * each call inserts a new ParentConsent row. marketing_opt_in on
     * ParentAccount is a separate, application-owned preference and is never
     * treated as consent here.
     */
    @Transactional
    public Optional<ParentConsent> recordConsent(String externalSubject, String consentType, String consentVersion) {
        ParentConsentType type = resolveConsentType(consentType);

        return parentAccountRepository.findByExternalSubject(externalSubject)
                .map(account -> parentConsentRepository.save(
                        new ParentConsent(account.getId(), type, consentVersion)));
    }

    private ParentConsentType resolveConsentType(String consentType) {
        try {
            return ParentConsentType.valueOf(consentType);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported consent type.");
        }
    }

    /**
     * Read-only lookup of the current consent state for an already-provisioned
     * parent. Never creates a ParentAccount and never writes a ParentConsent
     * row. Empty Optional means no Future Minds parent account exists for the
     * subject; an empty list (present Optional) means the account exists but
     * has not recorded consent - the two are never conflated.
     * <p>
     * Rows are append-only (see ParentConsent), so more than one row can exist
     * for the same consent type; only the most recently recorded row per type
     * is returned as "current".
     */
    public Optional<List<ParentConsent>> findCurrentConsents(String externalSubject) {
        return parentAccountRepository.findByExternalSubject(externalSubject)
                .map(account -> latestPerType(
                        parentConsentRepository.findByParentAccountIdOrderByRecordedAtDesc(account.getId())));
    }

    private List<ParentConsent> latestPerType(List<ParentConsent> consentsNewestFirst) {
        Map<ParentConsentType, ParentConsent> latestByType = new LinkedHashMap<>();
        for (ParentConsent consent : consentsNewestFirst) {
            latestByType.putIfAbsent(consent.getConsentType(), consent);
        }
        return new ArrayList<>(latestByType.values());
    }

    private ParentAccount applyProfileUpdate(ParentAccount account, String givenName, String familyName, Boolean marketingOptIn) {
        List<ParentProfileAudit> auditEvents = new ArrayList<>();

        if (givenName != null && account.updateGivenName(givenName)) {
            auditEvents.add(new ParentProfileAudit(account.getId(), ParentProfileChangeType.GIVEN_NAME_UPDATED));
        }

        if (familyName != null && account.updateFamilyName(familyName)) {
            auditEvents.add(new ParentProfileAudit(account.getId(), ParentProfileChangeType.FAMILY_NAME_UPDATED));
        }

        if (marketingOptIn != null && account.updateMarketingOptIn(marketingOptIn)) {
            auditEvents.add(new ParentProfileAudit(account.getId(), marketingOptIn
                    ? ParentProfileChangeType.MARKETING_PREFERENCE_ENABLED
                    : ParentProfileChangeType.MARKETING_PREFERENCE_DISABLED));
        }

        if (!auditEvents.isEmpty()) {
            parentProfileAuditRepository.saveAll(auditEvents);
        }

        return account;
    }

    private ProvisionResult syncIdentityProviderEmail(ParentAccount account, String email) {
        account.syncEmailFromIdentityProvider(email);
        return new ProvisionResult(account, false);
    }

    private ProvisionResult createOrRecoverFromRace(String externalSubject, String email, String givenName, String familyName) {
        try {
            ParentAccount created = parentAccountRepository.saveAndFlush(
                    new ParentAccount(externalSubject, email, givenName, familyName));
            log.info("Provisioned new parent account for subject={}", externalSubject);
            return new ProvisionResult(created, true);
        } catch (DataIntegrityViolationException raceLost) {
            ParentAccount existing = parentAccountRepository.findByExternalSubject(externalSubject)
                    .orElseThrow(() -> raceLost);
            log.debug("Lost concurrent provisioning race for subject={}, syncing existing row instead", externalSubject);
            return syncIdentityProviderEmail(existing, email);
        }
    }

    public record ProvisionResult(ParentAccount parentAccount, boolean created) {
    }
}
