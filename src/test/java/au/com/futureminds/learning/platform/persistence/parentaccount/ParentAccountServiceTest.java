package au.com.futureminds.learning.platform.persistence.parentaccount;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentAccountServiceTest {

    private final ParentAccountRepository parentAccountRepository = mock(ParentAccountRepository.class);
    private final ParentAccountService parentAccountService = new ParentAccountService(parentAccountRepository);

    private static final String SUBJECT = "keycloak-subject-123";

    @Test
    void firstCallCreatesExactlyOneParentAccount() {
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());
        ParentAccount saved = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountRepository.saveAndFlush(any(ParentAccount.class))).thenReturn(saved);

        ParentAccountService.ProvisionResult result =
                parentAccountService.provision(SUBJECT, "parent@example.com", "Ada", "Lovelace");

        assertThat(result.created()).isTrue();
        assertThat(result.parentAccount()).isSameAs(saved);
        verify(parentAccountRepository, times(1)).saveAndFlush(any(ParentAccount.class));
    }

    @Test
    void repeatedCallForSameSubjectIsIdempotentAndUpdatesProfile() {
        ParentAccount existing = new ParentAccount(SUBJECT, "old@example.com", "Old", "Name");
        when(parentAccountRepository.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(existing));

        ParentAccountService.ProvisionResult result =
                parentAccountService.provision(SUBJECT, "new@example.com", "New", "Name");

        assertThat(result.created()).isFalse();
        assertThat(result.parentAccount()).isSameAs(existing);
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getGivenName()).isEqualTo("New");
        verify(parentAccountRepository, never()).saveAndFlush(any());
        verify(parentAccountRepository, never()).save(any());
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
}
