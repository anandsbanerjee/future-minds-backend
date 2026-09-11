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
     */
    public Optional<List<Student>> findAllForParent(String externalSubject) {
        return parentAccountService.findByExternalSubject(externalSubject)
                .map(account -> studentRepository.findByParentAccountIdOrderByIdAsc(account.getId()));
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
     * a different parent.
     */
    public Optional<Student> findOneForParent(String externalSubject, Long studentId) {
        return parentAccountService.findByExternalSubject(externalSubject)
                .flatMap(account -> studentRepository.findByIdAndParentAccountId(studentId, account.getId()));
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
