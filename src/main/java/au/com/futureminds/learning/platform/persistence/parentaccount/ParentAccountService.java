package au.com.futureminds.learning.platform.persistence.parentaccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ParentAccountService {

    private static final Logger log = LoggerFactory.getLogger(ParentAccountService.class);

    private final ParentAccountRepository parentAccountRepository;

    public ParentAccountService(ParentAccountRepository parentAccountRepository) {
        this.parentAccountRepository = parentAccountRepository;
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
     */
    @Transactional
    public ProvisionResult provision(String externalSubject, String email, String givenName, String familyName) {
        return parentAccountRepository.findByExternalSubject(externalSubject)
                .map(existing -> syncExisting(existing, email, givenName, familyName))
                .orElseGet(() -> createOrRecoverFromRace(externalSubject, email, givenName, familyName));
    }

    private ProvisionResult syncExisting(ParentAccount account, String email, String givenName, String familyName) {
        account.syncProfile(email, givenName, familyName);
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
            return syncExisting(existing, email, givenName, familyName);
        }
    }

    public record ProvisionResult(ParentAccount parentAccount, boolean created) {
    }
}
