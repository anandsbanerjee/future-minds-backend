package au.com.futureminds.learning.platform.persistence.parentaccount;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentAccountServiceTest {

    private final ParentAccountRepository parentAccountRepository = mock(ParentAccountRepository.class);
    private final ParentProfileAuditRepository parentProfileAuditRepository = mock(ParentProfileAuditRepository.class);
    private final ParentConsentRepository parentConsentRepository = mock(ParentConsentRepository.class);
    private final ParentAccountService parentAccountService =
            new ParentAccountService(parentAccountRepository, parentProfileAuditRepository, parentConsentRepository);

    private static final String SUBJECT = "keycloak-subject-123";

    // --- provisioning ---

    @Test
    void firstCallCreatesExactlyOneParentAccount() {
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());
        ParentAccount saved = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountRepository.saveAndFlush(any(ParentAccount.class))).thenReturn(saved);

        ParentAccountService.ProvisionResult result =
                parentAccountService.provision(SUBJECT, "parent@example.com", "Ada", "Lovelace");

        assertThat(result.created()).isTrue();
        assertThat(result.parentAccount()).isSameAs(saved);
        assertThat(saved.isMarketingOptIn()).isFalse();
        verify(parentAccountRepository, times(1)).saveAndFlush(any(ParentAccount.class));
    }

    @Test
    void repeatedCallForSameSubjectSynchronisesEmailButPreservesLocallyEditedNames() {
        ParentAccount existing = new ParentAccount(SUBJECT, "old@example.com", "Old", "Name");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        ParentAccountService.ProvisionResult result =
                parentAccountService.provision(SUBJECT, "new@example.com", "New", "Name-From-Idp");

        assertThat(result.created()).isFalse();
        assertThat(result.parentAccount()).isSameAs(existing);
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getGivenName()).isEqualTo("Old");
        assertThat(existing.getFamilyName()).isEqualTo("Name");
        verify(parentAccountRepository, never()).saveAndFlush(any());
        verify(parentAccountRepository, never()).save(any());
        verify(parentProfileAuditRepository, never()).saveAll(any());
    }

    @Test
    void repeatedCallDoesNotOverwriteLocallyEditedMarketingOptIn() {
        ParentAccount existing = new ParentAccount(SUBJECT, "old@example.com", "Old", "Name");
        existing.updateMarketingOptIn(true);
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        parentAccountService.provision(SUBJECT, "new@example.com", "New", "New");

        assertThat(existing.isMarketingOptIn()).isTrue();
    }

    @Test
    void concurrentFirstCallForSameSubjectDoesNotCreateADuplicate() {
        ParentAccount concurrentlyCreated = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");

        when(parentAccountRepository.findByExternalSubject(SUBJECT))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentlyCreated));
        when(parentAccountRepository.saveAndFlush(any(ParentAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key: external_subject"));

        ParentAccountService.ProvisionResult result =
                parentAccountService.provision(SUBJECT, "parent@example.com", "Ada", "Lovelace");

        assertThat(result.created()).isFalse();
        assertThat(result.parentAccount()).isSameAs(concurrentlyCreated);
        verify(parentAccountRepository, times(1)).saveAndFlush(any(ParentAccount.class));
        verify(parentAccountRepository, times(2)).findByExternalSubject(SUBJECT);
    }

    @Test
    void provisioningIsScopedToExternalSubject() {
        when(parentAccountRepository.findByExternalSubject(any())).thenReturn(Optional.empty());
        when(parentAccountRepository.saveAndFlush(any(ParentAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<ParentAccount> captor = ArgumentCaptor.forClass(ParentAccount.class);

        parentAccountService.provision(SUBJECT, "shared@example.com", "Ada", "Lovelace");

        verify(parentAccountRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getExternalSubject()).isEqualTo(SUBJECT);
    }

    @Test
    void findByExternalSubjectReturnsTheExistingAccountWithoutWritingAnything() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        Optional<ParentAccount> result = parentAccountService.findByExternalSubject(SUBJECT);

        assertThat(result).contains(existing);
        verify(parentAccountRepository, never()).save(any());
        verify(parentAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void findByExternalSubjectReturnsEmptyForAnUnknownSubjectAndDoesNotCreateOne() {
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<ParentAccount> result = parentAccountService.findByExternalSubject(SUBJECT);

        assertThat(result).isEmpty();
        verify(parentAccountRepository, never()).save(any());
        verify(parentAccountRepository, never()).saveAndFlush(any());
    }

    // --- profile update ---

    @Test
    void updateProfileChangesGivenNameAndRecordsAuditEvent() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Old", "Name");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        Optional<ParentAccount> result = parentAccountService.updateProfile(SUBJECT, "New", null, null);

        assertThat(result).isPresent();
        assertThat(result.get().getGivenName()).isEqualTo("New");
        assertThat(result.get().getFamilyName()).isEqualTo("Name");

        List<ParentProfileAudit> audited = captureAuditedEvents();
        assertThat(audited).hasSize(1);
        assertThat(audited.get(0).getChangeType()).isEqualTo(ParentProfileChangeType.GIVEN_NAME_UPDATED);
    }

    @Test
    void updateProfileChangesFamilyNameAndRecordsAuditEvent() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Old", "Name");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        parentAccountService.updateProfile(SUBJECT, null, "NewFamily", null);

        List<ParentProfileAudit> audited = captureAuditedEvents();
        assertThat(audited).hasSize(1);
        assertThat(audited.get(0).getChangeType()).isEqualTo(ParentProfileChangeType.FAMILY_NAME_UPDATED);
    }

    @Test
    void updateProfileEnablingMarketingOptInRecordsEnabledAuditEvent() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Old", "Name");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        parentAccountService.updateProfile(SUBJECT, null, null, true);

        assertThat(existing.isMarketingOptIn()).isTrue();
        List<ParentProfileAudit> audited = captureAuditedEvents();
        assertThat(audited).hasSize(1);
        assertThat(audited.get(0).getChangeType()).isEqualTo(ParentProfileChangeType.MARKETING_PREFERENCE_ENABLED);
    }

    @Test
    void updateProfileDisablingMarketingOptInRecordsDisabledAuditEvent() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Old", "Name");
        existing.updateMarketingOptIn(true);
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        parentAccountService.updateProfile(SUBJECT, null, null, false);

        assertThat(existing.isMarketingOptIn()).isFalse();
        List<ParentProfileAudit> audited = captureAuditedEvents();
        assertThat(audited).hasSize(1);
        assertThat(audited.get(0).getChangeType()).isEqualTo(ParentProfileChangeType.MARKETING_PREFERENCE_DISABLED);
    }

    @Test
    void updateProfileWithNoActualValueChangeRecordsNoAuditEvent() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Old", "Name");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        parentAccountService.updateProfile(SUBJECT, "Old", "Name", false);

        verify(parentProfileAuditRepository, never()).saveAll(any());
    }

    @Test
    void updateProfileLeavesOmittedFieldsUnchanged() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Old", "Name");
        existing.updateMarketingOptIn(true);
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        Optional<ParentAccount> result = parentAccountService.updateProfile(SUBJECT, "New", null, null);

        assertThat(result.get().getFamilyName()).isEqualTo("Name");
        assertThat(result.get().isMarketingOptIn()).isTrue();
    }

    @Test
    void updateProfileReturnsEmptyWhenNoAccountExistsForSubject() {
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<ParentAccount> result = parentAccountService.updateProfile(SUBJECT, "New", null, null);

        assertThat(result).isEmpty();
        verify(parentProfileAuditRepository, never()).saveAll(any());
    }

    @Test
    void updateProfileNeverInvokesProvisioningCreateLogic() {
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        parentAccountService.updateProfile(SUBJECT, "New", null, null);

        verify(parentAccountRepository, never()).saveAndFlush(any());
        verify(parentAccountRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    private List<ParentProfileAudit> captureAuditedEvents() {
        ArgumentCaptor<List<ParentProfileAudit>> captor = ArgumentCaptor.forClass(List.class);
        verify(parentProfileAuditRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // --- consent ---

    @Test
    void recordConsentCreatesExactlyOneParentConsentRecord() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));
        when(parentConsentRepository.save(any(ParentConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ParentConsent> result = parentAccountService.recordConsent(SUBJECT, "PRIVACY_POLICY", "v1");

        assertThat(result).isPresent();
        ArgumentCaptor<ParentConsent> captor = ArgumentCaptor.forClass(ParentConsent.class);
        verify(parentConsentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getParentAccountId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getConsentType()).isEqualTo(ParentConsentType.PRIVACY_POLICY);
        assertThat(captor.getValue().getConsentVersion()).isEqualTo("v1");
    }

    @Test
    void recordConsentReturnsEmptyWhenNoParentAccountExistsForSubject() {
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<ParentConsent> result = parentAccountService.recordConsent(SUBJECT, "PRIVACY_POLICY", "v1");

        assertThat(result).isEmpty();
        verify(parentConsentRepository, never()).save(any());
    }

    @Test
    void recordConsentNeverAutoProvisionsAParentAccount() {
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        parentAccountService.recordConsent(SUBJECT, "PRIVACY_POLICY", "v1");

        verify(parentAccountRepository, never()).save(any());
        verify(parentAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void recordConsentRejectsAnUnsupportedConsentType() {
        assertThatThrownBy(() -> parentAccountService.recordConsent(SUBJECT, "UNKNOWN_TYPE", "v1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(parentAccountRepository, never()).findByExternalSubject(any());
        verify(parentConsentRepository, never()).save(any());
    }

    @Test
    void recordingConsentTwiceCreatesTwoSeparateImmutableRows() {
        ParentAccount existing = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));
        when(parentConsentRepository.save(any(ParentConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        parentAccountService.recordConsent(SUBJECT, "PRIVACY_POLICY", "v1");
        parentAccountService.recordConsent(SUBJECT, "PRIVACY_POLICY", "v2");

        ArgumentCaptor<ParentConsent> captor = ArgumentCaptor.forClass(ParentConsent.class);
        verify(parentConsentRepository, times(2)).save(captor.capture());
        verify(parentConsentRepository, never()).findById(any());
        verify(parentConsentRepository, never()).saveAndFlush(any());

        List<ParentConsent> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0)).isNotSameAs(saved.get(1));
        assertThat(saved.get(0).getConsentVersion()).isEqualTo("v1");
        assertThat(saved.get(1).getConsentVersion()).isEqualTo("v2");
    }
}
