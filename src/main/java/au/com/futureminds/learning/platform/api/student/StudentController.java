package au.com.futureminds.learning.platform.api.student;

import au.com.futureminds.learning.platform.api.ApiPaths;
import au.com.futureminds.learning.platform.persistence.student.Student;
import au.com.futureminds.learning.platform.persistence.student.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.V1 + "/parents/me/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Ownership is taken solely from the validated JWT subject - the request
     * body carries only student attributes, never a parent/owner identifier,
     * so a caller cannot nominate another parent.
     */
    @PostMapping
    public ResponseEntity<StudentResponse> createStudent(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody CreateStudentRequest request) {
        Student student = studentService.create(
                        jwt.getSubject(),
                        request.firstName(),
                        request.schoolYear(),
                        request.preparationGoal())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parent account not found."));

        return ResponseEntity.status(HttpStatus.CREATED).body(StudentResponse.from(student));
    }

    /**
     * Read-only - identity is taken solely from the validated JWT subject, so
     * a caller can never retrieve another parent's students. An empty list
     * means the parent account exists but has no students yet; a 404 means
     * no Future Minds parent account exists for the subject.
     */
    @GetMapping
    public ResponseEntity<List<StudentResponse>> getMyStudents(@AuthenticationPrincipal Jwt jwt) {
        List<Student> students = studentService.findAllForParent(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parent account not found."));

        return ResponseEntity.ok(students.stream().map(StudentResponse::from).toList());
    }

    /**
     * Read-only - identity is taken solely from the validated JWT subject and
     * combined with the path studentId in the same ownership-scoped
     * repository query, so a caller can never retrieve another parent's
     * student. A 404 is returned whether the student doesn't exist, belongs
     * to another parent, or no Future Minds parent account exists for the
     * subject - the response never reveals which.
     */
    @GetMapping("/{studentId}")
    public ResponseEntity<StudentResponse> getMyStudent(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable Long studentId) {
        Student student = studentService.findOneForParent(jwt.getSubject(), studentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Student not found."));

        return ResponseEntity.ok(StudentResponse.from(student));
    }

    /**
     * Application-owned profile edit - identity is taken solely from the
     * validated JWT subject and combined with the path studentId in the same
     * ownership-scoped repository query used by getMyStudent, so a caller can
     * never update another parent's student. The request body carries only
     * editable student attributes, never a parent/owner identifier. A 404 is
     * returned whether the student doesn't exist, belongs to another parent,
     * or no Future Minds parent account exists for the subject - the
     * response never reveals which.
     */
    @PatchMapping("/{studentId}")
    public ResponseEntity<StudentResponse> updateMyStudent(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable Long studentId,
                                                             @Valid @RequestBody UpdateStudentRequest request) {
        Student student = studentService.updateForParent(
                        jwt.getSubject(),
                        studentId,
                        request.firstName(),
                        request.schoolYear(),
                        request.preparationGoal())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Student not found."));

        return ResponseEntity.ok(StudentResponse.from(student));
    }
}
