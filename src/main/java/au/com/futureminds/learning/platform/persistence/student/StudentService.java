package au.com.futureminds.learning.platform.persistence.student;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
