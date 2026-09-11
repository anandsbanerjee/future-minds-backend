package au.com.futureminds.learning.platform.persistence.student;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccount;
import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class StudentServiceTest {

    private final ParentAccountService parentAccountService = mock(ParentAccountService.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentService studentService = new StudentService(parentAccountService, studentRepository);

    private static final String SUBJECT = "keycloak-subject-abc";
    private static final String OTHER_SUBJECT = "keycloak-subject-xyz";

    @Test
    void resolvesParentByTheAuthenticatedExternalSubject() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS");

        verify(parentAccountService).findByExternalSubject(SUBJECT);
    }

    @Test
    void persistsTheStudentAgainstTheResolvedInternalParentAccountId() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS");

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).saveAndFlush(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getParentAccountId()).isEqualTo(42L);
        assertThat(result).isPresent();
        assertThat(result.get().getParentAccountId()).isEqualTo(42L);
    }

    @Test
    void savesTheStudentForAResolvedParent() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS");

        verify(studentRepository).saveAndFlush(any(Student.class));
    }

    @Test
    void doesNotSaveAStudentWhenNoParentAccountExists() {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS");

        assertThat(result).isEmpty();
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void persistsValidSchoolYearAndPreparationGoal() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.create(SUBJECT, "Aarav", "YEAR_5", "YEAR_5_MATHEMATICS");

        assertThat(result).isPresent();
        assertThat(result.get().getSchoolYear()).isEqualTo(SchoolYear.YEAR_5);
        assertThat(result.get().getPreparationGoal()).isEqualTo(PreparationGoal.YEAR_5_MATHEMATICS);
        assertThat(result.get().getFirstName()).isEqualTo("Aarav");
    }

    @Test
    void rejectsAnUnsupportedSchoolYearBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> studentService.create(SUBJECT, "Aarav", "YEAR_9", "SELECTIVE_MATHEMATICS"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verify(parentAccountService, never()).findByExternalSubject(any());
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnUnsupportedPreparationGoalBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> studentService.create(SUBJECT, "Aarav", "YEAR_5", "UNKNOWN_GOAL"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verify(parentAccountService, never()).findByExternalSubject(any());
        verify(studentRepository, never()).saveAndFlush(any());
    }

    // --- duplicate detection ---

    @Test
    void rejectsACreateThatDuplicatesAnExistingStudentForTheSameParent() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal(
                eq(42L), eq("Aarav"), eq(SchoolYear.YEAR_5), eq(PreparationGoal.SELECTIVE_MATHEMATICS)))
                .thenReturn(true);

        assertThatThrownBy(() -> studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAConcurrentDuplicateCreateThatRacesPastTheExistenceCheck() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal(
                any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void allowsTwoDifferentStudentsForTheSameParent() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal(
                any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> first = studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS");
        Optional<Student> second = studentService.create(SUBJECT, "Priya", "YEAR_5", "SELECTIVE_MATHEMATICS");

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        verify(studentRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Student.class));
    }

    // --- list students for the authenticated parent ---

    @Test
    void returnsAllStudentsForTheResolvedParentInIdOrder() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student first = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        Student second = new Student(42L, "Priya", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByParentAccountIdOrderByIdAsc(42L)).thenReturn(List.of(first, second));

        Optional<List<Student>> result = studentService.findAllForParent(SUBJECT);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(first, second);
    }

    @Test
    void returnsAnEmptyListWhenTheResolvedParentHasNoStudents() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByParentAccountIdOrderByIdAsc(42L)).thenReturn(List.of());

        Optional<List<Student>> result = studentService.findAllForParent(SUBJECT);

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void returnsEmptyOptionalWhenNoParentAccountExistsForListingStudents() {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<List<Student>> result = studentService.findAllForParent(SUBJECT);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).findByParentAccountIdOrderByIdAsc(any());
    }

    @Test
    void queriesOnlyByTheInternallyResolvedParentAccountIdNeverAnotherParents() {
        ParentAccount parentA = new ParentAccount(SUBJECT, "a@example.com", "Ada", "Lovelace");
        setId(parentA, 42L);
        Student parentAsStudent = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(parentA));
        when(studentRepository.findByParentAccountIdOrderByIdAsc(42L)).thenReturn(List.of(parentAsStudent));

        studentService.findAllForParent(SUBJECT);

        ArgumentCaptor<Long> parentAccountIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(studentRepository).findByParentAccountIdOrderByIdAsc(parentAccountIdCaptor.capture());
        assertThat(parentAccountIdCaptor.getValue()).isEqualTo(42L);
    }

    // --- get one student for the authenticated parent ---

    @Test
    void resolvesParentByTheAuthenticatedExternalSubjectWhenGettingOneStudent() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(1L, 42L)).thenReturn(Optional.of(student));

        studentService.findOneForParent(SUBJECT, 1L);

        verify(parentAccountService).findByExternalSubject(SUBJECT);
    }

    @Test
    void queriesTheRepositoryUsingBothTheStudentIdAndTheResolvedInternalParentAccountId() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(1L, 42L)).thenReturn(Optional.of(student));

        Optional<Student> result = studentService.findOneForParent(SUBJECT, 1L);

        ArgumentCaptor<Long> studentIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> parentAccountIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(studentRepository).findByIdAndParentAccountId(studentIdCaptor.capture(), parentAccountIdCaptor.capture());
        assertThat(studentIdCaptor.getValue()).isEqualTo(1L);
        assertThat(parentAccountIdCaptor.getValue()).isEqualTo(42L);
        assertThat(result).contains(student);
    }

    @Test
    void doesNotQueryTheRepositoryWhenNoParentAccountExistsForGettingOneStudent() {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.findOneForParent(SUBJECT, 1L);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).findByIdAndParentAccountId(any(), any());
    }

    @Test
    void returnsEmptyWhenTheOwnershipScopedLookupFindsNothing() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(999L, 42L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.findOneForParent(SUBJECT, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void aStudentBelongingToAnotherParentCannotBeRetrievedUsingOnlyItsId() {
        ParentAccount otherParent = new ParentAccount(OTHER_SUBJECT, "other@example.com", "Grace", "Hopper");
        setId(otherParent, 99L);
        when(parentAccountService.findByExternalSubject(OTHER_SUBJECT)).thenReturn(Optional.of(otherParent));
        when(studentRepository.findByIdAndParentAccountId(1L, 99L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.findOneForParent(OTHER_SUBJECT, 1L);

        assertThat(result).isEmpty();
        verify(studentRepository).findByIdAndParentAccountId(1L, 99L);
    }

    private static void setId(ParentAccount account, Long id) {
        try {
            var field = ParentAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
