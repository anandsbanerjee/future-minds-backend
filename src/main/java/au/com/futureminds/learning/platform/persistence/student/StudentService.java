package au.com.futureminds.learning.platform.persistence.student;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class StudentService {

    private final ParentAccountService parentAccountService;
    private final StudentRepository studentRepository;

    public StudentService(ParentAccountService parentAccountService, StudentRepository studentRepository) {
        this.parentAccountService = parentAccountService;
        this.studentRepository = studentRepository;
    }

    /**
     * Ownership is derived solely from the authenticated external subject via
     * ParentAccountService.findByExternalSubject - callers can never supply a
     * parentAccountId directly, and a missing Future Minds parent account is
     * never provisioned as a side effect. Empty Optional means no such
     * account exists, so the controller can produce the standard 404 used
     * across /me/* endpoints.
     * <p>
     * A student is considered a duplicate of an existing one for the same
     * parent when firstName/schoolYear/preparationGoal all match exactly - a
     * repeat create (e.g. an accidental double-submit) is rejected with 409
     * rather than silently inserting a second identical row. The existence
     * check is a fast path; the unique constraint on the student table is the
     * authority of last resort for a concurrent duplicate create (same
     * race-recovery approach as ParentAccountService.provision).
     */
    @Transactional
    public Optional<Student> create(String externalSubject, String firstName, String schoolYear, String preparationGoal) {
        SchoolYear resolvedSchoolYear = resolveSchoolYear(schoolYear);
        PreparationGoal resolvedPreparationGoal = resolvePreparationGoal(preparationGoal);

        return parentAccountService.findByExternalSubject(externalSubject)
                .map(account -> createForParent(account.getId(), firstName, resolvedSchoolYear, resolvedPreparationGoal));
    }

    private Student createForParent(Long parentAccountId, String firstName, SchoolYear schoolYear, PreparationGoal preparationGoal) {
        if (studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal(
                parentAccountId, firstName, schoolYear, preparationGoal)) {
            throw duplicateStudentException();
        }

        try {
            return studentRepository.saveAndFlush(new Student(parentAccountId, firstName, schoolYear, preparationGoal));
        } catch (DataIntegrityViolationException raceLost) {
            throw duplicateStudentException();
        }
    }

    /**
     * Read-only lookup of the authenticated parent's own students. Ownership
     * is derived solely from the resolved ParentAccount.id - never from a
     * client-supplied identifier - so this can never return another parent's
     * students. Empty Optional means no Future Minds parent account exists
     * for the subject; a present-but-empty list means the account exists and
     * simply has no students yet, so the controller returns 200 [] rather
     * than 404. Students are returned in ID (creation) order for a
     * deterministic, predictable response without introducing sorting/paging.
     * Deactivated students (EP-06.9) are excluded - a soft-deactivated
     * student is never returned by the normal active-student APIs.
     */
    public Optional<List<Student>> findAllForParent(String externalSubject) {
        return parentAccountService.findByExternalSubject(externalSubject)
                .map(account -> studentRepository.findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(account.getId()));
    }

    /**
     * Read-only lookup of a single student belonging to the authenticated
     * parent. Ownership is enforced by the repository query itself -
     * findByIdAndParentAccountId requires both the requested studentId and
     * the resolved internal parentAccountId to match in the same query -
     * never by fetching-then-checking, so a student belonging to another
     * parent is indistinguishable from a non-existent one. Empty Optional
     * covers all three cases the controller maps to 404: no Future Minds
     * parent account for the subject, no such student, or a student owned by
     * a different parent. A deactivated student (EP-06.9) is likewise folded
     * into this same empty case - once deactivated, a student is
     * indistinguishable from nonexistent through this lookup.
     */
    public Optional<Student> findOneForParent(String externalSubject, Long studentId) {
        return parentAccountService.findByExternalSubject(externalSubject)
                .flatMap(account -> studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(studentId, account.getId()));
    }

    /**
     * Application-owned profile edit, entirely separate from creation.
     * Ownership is enforced by the same active-only, ownership-scoped lookup
     * findOneForParent uses - never findById followed by a Java-side
     * ownership check - so a student belonging to another parent, or a
     * student that has been deactivated (EP-06.9), is indistinguishable from
     * a non-existent one here too. Only fields that
     * actually change are mutated via Student's own equality-guarded
     * mutators, so a true no-op update dirties nothing. saveAndFlush is
     * still called (mirroring create's race-recovery approach) purely to
     * force the flush inside this method's try/catch - without it, a
     * concurrent racing update would only fail at transaction commit, past
     * this method's own exception handling, and surface as an unmapped 500
     * instead of the same 409 create returns for the same race; Hibernate
     * still emits no UPDATE at all when nothing was actually mutated, so
     * updatedAt is never bumped by a no-op call.
     * <p>
     * The duplicate check mirrors create's, but excludes the student's own
     * id so an unchanged (or changed-back-to-itself) record never collides
     * with itself; changing the effective firstName/schoolYear/
     * preparationGoal onto a different existing student for the same parent
     * still results in the same 409 as create. The unique constraint remains
     * the authority of last resort for a concurrent racing update.
     */
    @Transactional
    public Optional<Student> updateForParent(String externalSubject, Long studentId,
                                              String firstName, String schoolYear, String preparationGoal) {
        return parentAccountService.findByExternalSubject(externalSubject)
                .flatMap(account -> studentRepository.findByIdAndParentAccountIdAndDeactivatedAtIsNull(studentId, account.getId()))
                .map(student -> applyUpdate(student, firstName, schoolYear, preparationGoal));
    }

    private Student applyUpdate(Student student, String firstName, String schoolYear, String preparationGoal) {
        String effectiveFirstName = firstName != null ? firstName : student.getFirstName();
        SchoolYear effectiveSchoolYear = schoolYear != null ? resolveSchoolYear(schoolYear) : student.getSchoolYear();
        PreparationGoal effectivePreparationGoal = preparationGoal != null
                ? resolvePreparationGoal(preparationGoal) : student.getPreparationGoal();

        if (studentRepository.existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
                student.getParentAccountId(), effectiveFirstName, effectiveSchoolYear, effectivePreparationGoal, student.getId())) {
            throw duplicateStudentException();
        }

        student.updateFirstName(effectiveFirstName);
        student.updateSchoolYear(effectiveSchoolYear);
        student.updatePreparationGoal(effectivePreparationGoal);

        try {
            return studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException raceLost) {
            throw duplicateStudentException();
        }
    }

    /**
     * Soft deactivation only (EP-06.9) - never a physical delete. Ownership
     * uses the unfiltered findByIdAndParentAccountId, not the active-only
     * lookup findOneForParent/updateForParent use, so that a student the
     * caller has already deactivated is still resolvable here - a repeat
     * call must be able to find its own target rather than falling through
     * to the not-found case. A student belonging to another parent is still
     * indistinguishable from a non-existent one: the ownership predicate is
     * unchanged, only the active-state filter is dropped for this one
     * lookup. Student.deactivate() is itself idempotent (false, no mutation,
     * if already deactivated), so a repeat call here is a safe no-op that
     * still reports success - persistence is skipped entirely when no
     * transition actually occurred, so a repeat call never re-stamps
     * deactivatedAt or bumps updatedAt.
     */
    @Transactional
    public Optional<Student> deactivateForParent(String externalSubject, Long studentId) {
        return parentAccountService.findByExternalSubject(externalSubject)
                .flatMap(account -> studentRepository.findByIdAndParentAccountId(studentId, account.getId()))
                .map(this::deactivate);
    }

    private Student deactivate(Student student) {
        if (student.deactivate()) {
            return studentRepository.saveAndFlush(student);
        }
        return student;
    }

    private ResponseStatusException duplicateStudentException() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "A student with this name, school year and preparation goal already exists for this parent.");
    }

    private SchoolYear resolveSchoolYear(String schoolYear) {
        try {
            return SchoolYear.valueOf(schoolYear);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported school year.");
        }
    }

    private PreparationGoal resolvePreparationGoal(String preparationGoal) {
        try {
            return PreparationGoal.valueOf(preparationGoal);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported preparation goal.");
        }
    }
}
