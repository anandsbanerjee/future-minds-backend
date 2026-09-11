package au.com.futureminds.learning.platform.persistence.student;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccount;
import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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
        when(studentRepository.findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(42L)).thenReturn(List.of(first, second));

        Optional<List<Student>> result = studentService.findAllForParent(SUBJECT);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(first, second);
    }

    @Test
    void returnsAnEmptyListWhenTheResolvedParentHasNoStudents() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(42L)).thenReturn(List.of());

        Optional<List<Student>> result = studentService.findAllForParent(SUBJECT);

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void returnsEmptyOptionalWhenNoParentAccountExistsForListingStudents() {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<List<Student>> result = studentService.findAllForParent(SUBJECT);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(any());
    }

    @Test
    void queriesOnlyByTheInternallyResolvedParentAccountIdNeverAnotherParents() {
        ParentAccount parentA = new ParentAccount(SUBJECT, "a@example.com", "Ada", "Lovelace");
        setId(parentA, 42L);
        Student parentAsStudent = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(parentA));
        when(studentRepository.findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(42L)).thenReturn(List.of(parentAsStudent));

        studentService.findAllForParent(SUBJECT);

        ArgumentCaptor<Long> parentAccountIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(studentRepository).findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(parentAccountIdCaptor.capture());
        assertThat(parentAccountIdCaptor.getValue()).isEqualTo(42L);
    }

    // --- get one student for the authenticated parent ---

    @Test
    void resolvesParentByTheAuthenticatedExternalSubjectWhenGettingOneStudent() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));

        studentService.findOneForParent(SUBJECT, 1L);

        verify(parentAccountService).findByExternalSubject(SUBJECT);
    }

    @Test
    void queriesTheRepositoryUsingBothTheStudentIdAndTheResolvedInternalParentAccountId() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));

        Optional<Student> result = studentService.findOneForParent(SUBJECT, 1L);

        ArgumentCaptor<Long> studentIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> parentAccountIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(studentRepository).findByIdAndParentAccountIdAndDeactivatedAtIsNull(studentIdCaptor.capture(), parentAccountIdCaptor.capture());
        assertThat(studentIdCaptor.getValue()).isEqualTo(1L);
        assertThat(parentAccountIdCaptor.getValue()).isEqualTo(42L);
        assertThat(result).contains(student);
    }

    @Test
    void doesNotQueryTheRepositoryWhenNoParentAccountExistsForGettingOneStudent() {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.findOneForParent(SUBJECT, 1L);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).findByIdAndParentAccountIdAndDeactivatedAtIsNull(any(), any());
    }

    @Test
    void returnsEmptyWhenTheOwnershipScopedLookupFindsNothing() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(999L, 42L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.findOneForParent(SUBJECT, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void aStudentBelongingToAnotherParentCannotBeRetrievedUsingOnlyItsId() {
        ParentAccount otherParent = new ParentAccount(OTHER_SUBJECT, "other@example.com", "Grace", "Hopper");
        setId(otherParent, 99L);
        when(parentAccountService.findByExternalSubject(OTHER_SUBJECT)).thenReturn(Optional.of(otherParent));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 99L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.findOneForParent(OTHER_SUBJECT, 1L);

        assertThat(result).isEmpty();
        verify(studentRepository).findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 99L);
    }

    // --- update student for the authenticated parent ---

    @Test
    void ownerCanUpdateOwnStudentSuccessfully() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, "Priya", "YEAR_5", "YEAR_5_MATHEMATICS");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Priya");
        assertThat(result.get().getPreparationGoal()).isEqualTo(PreparationGoal.YEAR_5_MATHEMATICS);
    }

    @Test
    void updatesOnlyFirstNameWhenOtherFieldsAreOmitted() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, "Priya", null, null);

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Priya");
        assertThat(result.get().getSchoolYear()).isEqualTo(SchoolYear.YEAR_5);
        assertThat(result.get().getPreparationGoal()).isEqualTo(PreparationGoal.SELECTIVE_MATHEMATICS);
    }

    @Test
    void updatesOnlySchoolYearWhenOtherFieldsAreOmitted() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, null, "YEAR_5", null);

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Aarav");
        assertThat(result.get().getSchoolYear()).isEqualTo(SchoolYear.YEAR_5);
        assertThat(result.get().getPreparationGoal()).isEqualTo(PreparationGoal.SELECTIVE_MATHEMATICS);
    }

    @Test
    void updatesOnlyPreparationGoalWhenOtherFieldsAreOmitted() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, null, null, "YEAR_5_MATHEMATICS");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Aarav");
        assertThat(result.get().getSchoolYear()).isEqualTo(SchoolYear.YEAR_5);
        assertThat(result.get().getPreparationGoal()).isEqualTo(PreparationGoal.YEAR_5_MATHEMATICS);
    }

    @Test
    void unchangedValuesAreAcceptedAsANoOp() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.updateForParent(
                SUBJECT, 1L, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Aarav");
        assertThat(result.get().getSchoolYear()).isEqualTo(SchoolYear.YEAR_5);
        assertThat(result.get().getPreparationGoal()).isEqualTo(PreparationGoal.SELECTIVE_MATHEMATICS);
    }

    @Test
    void emptyUpdateRequestIsAcceptedAsANoOp() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, null, null, null);

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Aarav");
        assertThat(result.get().getSchoolYear()).isEqualTo(SchoolYear.YEAR_5);
        assertThat(result.get().getPreparationGoal()).isEqualTo(PreparationGoal.SELECTIVE_MATHEMATICS);
    }

    @Test
    void rejectsAnUnsupportedSchoolYearOnUpdateBeforeTouchingTheRepositoryDuplicateCheck() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.updateForParent(SUBJECT, 1L, null, "YEAR_9", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verify(studentRepository, never()).existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any());
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnUnsupportedPreparationGoalOnUpdateBeforeTouchingTheRepositoryDuplicateCheck() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentService.updateForParent(SUBJECT, 1L, null, null, "UNKNOWN_GOAL"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void doesNotQueryTheRepositoryWhenNoParentAccountExistsForUpdatingAStudent() {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, "Priya", null, null);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).findByIdAndParentAccountIdAndDeactivatedAtIsNull(any(), any());
    }

    @Test
    void returnsEmptyWhenTheStudentToUpdateDoesNotExist() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(999L, 42L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.updateForParent(SUBJECT, 999L, "Priya", null, null);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void aStudentBelongingToAnotherParentCannotBeUpdatedUsingOnlyItsId() {
        ParentAccount otherParent = new ParentAccount(OTHER_SUBJECT, "other@example.com", "Grace", "Hopper");
        setId(otherParent, 99L);
        when(parentAccountService.findByExternalSubject(OTHER_SUBJECT)).thenReturn(Optional.of(otherParent));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 99L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.updateForParent(OTHER_SUBJECT, 1L, "Priya", null, null);

        assertThat(result).isEmpty();
        verify(studentRepository).findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 99L);
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCollidingWithADifferentStudentForTheSameParentReturnsConflict() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                eq(42L), eq("Priya"), eq(SchoolYear.YEAR_5), eq(PreparationGoal.SELECTIVE_MATHEMATICS), eq(1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> studentService.updateForParent(SUBJECT, 1L, "Priya", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        verify(studentRepository, never()).saveAndFlush(any());
        assertThat(student.getFirstName()).isEqualTo("Aarav");
    }

    @Test
    void theDuplicateCheckExcludesTheStudentsOwnId() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentService.updateForParent(SUBJECT, 1L, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS");

        ArgumentCaptor<Long> idNotCaptor = ArgumentCaptor.forClass(Long.class);
        verify(studentRepository).existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                eq(42L), eq("Aarav"), eq(SchoolYear.YEAR_5), eq(PreparationGoal.SELECTIVE_MATHEMATICS), idNotCaptor.capture());
        assertThat(idNotCaptor.getValue()).isEqualTo(1L);
    }

    @Test
    void rejectsAConcurrentDuplicateUpdateThatRacesPastTheExistenceCheck() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> studentService.updateForParent(SUBJECT, 1L, "Priya", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void updatePreservesCreatedAtAndParentAccountId() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, "Priya", null, null);

        assertThat(result).isPresent();
        assertThat(result.get().getCreatedAt()).isEqualTo(student.getCreatedAt());
        assertThat(result.get().getParentAccountId()).isEqualTo(42L);
    }

    // --- deactivated students are excluded from active-only lookups (EP-06.9) ---

    @Test
    void aDeactivatedStudentIsNotReturnedByGetOne() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.findOneForParent(SUBJECT, 1L);

        assertThat(result).isEmpty();
    }

    @Test
    void aDeactivatedStudentCannotBeUpdated() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(1L, 42L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.updateForParent(SUBJECT, 1L, "Priya", null, null);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void aDeactivatedStudentIsExcludedFromTheList() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student active = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(42L)).thenReturn(List.of(active));

        Optional<List<Student>> result = studentService.findAllForParent(SUBJECT);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(active);
    }

    // --- deactivate student for the authenticated parent (EP-06.9) ---

    @Test
    void resolvesParentByTheAuthenticatedExternalSubjectWhenDeactivating() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentService.deactivateForParent(SUBJECT, 1L);

        verify(parentAccountService).findByExternalSubject(SUBJECT);
    }

    @Test
    void ownerCanDeactivateOwnActiveStudent() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Student> result = studentService.deactivateForParent(SUBJECT, 1L);

        assertThat(result).isPresent();
        assertThat(result.get().getDeactivatedAt()).isNotNull();
        verify(studentRepository).saveAndFlush(student);
    }

    @Test
    void repeatedDeactivationOfAnAlreadyDeactivatedStudentIsIdempotentAndDoesNotPersistAgain() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        student.deactivate();
        LocalDateTime firstDeactivatedAt = student.getDeactivatedAt();
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(1L, 42L)).thenReturn(Optional.of(student));

        Optional<Student> result = studentService.deactivateForParent(SUBJECT, 1L);

        assertThat(result).isPresent();
        assertThat(result.get().getDeactivatedAt()).isEqualTo(firstDeactivatedAt);
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void aStudentBelongingToAnotherParentCannotBeDeactivated() {
        ParentAccount otherParent = new ParentAccount(OTHER_SUBJECT, "other@example.com", "Grace", "Hopper");
        setId(otherParent, 99L);
        when(parentAccountService.findByExternalSubject(OTHER_SUBJECT)).thenReturn(Optional.of(otherParent));
        when(studentRepository.findByIdAndParentAccountId(1L, 99L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.deactivateForParent(OTHER_SUBJECT, 1L);

        assertThat(result).isEmpty();
        verify(studentRepository).findByIdAndParentAccountId(1L, 99L);
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsEmptyOptionalWhenNoParentAccountExistsForDeactivation() {
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.deactivateForParent(SUBJECT, 1L);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).findByIdAndParentAccountId(any(), any());
    }

    @Test
    void returnsEmptyWhenTheStudentToDeactivateDoesNotExist() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(999L, 42L)).thenReturn(Optional.empty());

        Optional<Student> result = studentService.deactivateForParent(SUBJECT, 999L);

        assertThat(result).isEmpty();
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivationUsesTheUnfilteredOwnershipLookupSoAnAlreadyDeactivatedOwnStudentIsStillFound() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentService.deactivateForParent(SUBJECT, 1L);

        verify(studentRepository).findByIdAndParentAccountId(1L, 42L);
        verify(studentRepository, never()).findByIdAndParentAccountIdAndDeactivatedAtIsNull(any(), any());
    }

    @Test
    void deactivationNeverPhysicallyDeletesTheStudentRow() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        Student student = new Student(42L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        setStudentId(student, 1L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        when(studentRepository.findByIdAndParentAccountId(1L, 42L)).thenReturn(Optional.of(student));
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        studentService.deactivateForParent(SUBJECT, 1L);

        verify(studentRepository, never()).deleteById(any());
        verify(studentRepository, never()).delete(any());
    }

    // --- duplicate rule is unchanged by deactivation (EP-06.9 accepted MVP limitation) ---

    @Test
    void createStillBlocksRecreatingAnIdenticalStudentEvenIfTheMatchingRowIsDeactivated() {
        ParentAccount account = new ParentAccount(SUBJECT, "parent@example.com", "Ada", "Lovelace");
        setId(account, 42L);
        when(parentAccountService.findByExternalSubject(SUBJECT)).thenReturn(Optional.of(account));
        // existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal is
        // deliberately unchanged by EP-06.9 - it has no deactivated_at
        // predicate, so it still reports true for a matching row regardless
        // of that row's lifecycle state. A deactivated student therefore
        // still blocks recreation of an identical profile in this MVP.
        when(studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal(
                eq(42L), eq("Aarav"), eq(SchoolYear.YEAR_5), eq(PreparationGoal.SELECTIVE_MATHEMATICS)))
                .thenReturn(true);

        assertThatThrownBy(() -> studentService.create(SUBJECT, "Aarav", "YEAR_5", "SELECTIVE_MATHEMATICS"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        verify(studentRepository, never()).saveAndFlush(any());
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

    private static void setStudentId(Student student, Long id) {
        try {
            var field = Student.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
