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
}
