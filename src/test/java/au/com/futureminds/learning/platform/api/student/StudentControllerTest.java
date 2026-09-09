package au.com.futureminds.learning.platform.api.student;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import au.com.futureminds.learning.platform.persistence.student.PreparationGoal;
import au.com.futureminds.learning.platform.persistence.student.SchoolYear;
import au.com.futureminds.learning.platform.persistence.student.Student;
import au.com.futureminds.learning.platform.persistence.student.StudentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentControllerTest {

    private static final String POST_STUDENTS_URI = "/api/v1/parents/me/students";
    private static final String SUBJECT = "keycloak-subject-abc";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    // Full application context also boots ParentController; the "test" profile
    // excludes DataSource/JPA autoconfiguration, so ParentAccountService's
    // repository dependency must be mocked out here too.
    @MockitoBean
    private ParentAccountService parentAccountService;

    // --- security ---

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post(POST_STUDENTS_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());

        verify(studentService, never()).create(any(), any(), any(), any());
    }

    @Test
    void authenticatedNonParentRequestIsForbidden() throws Exception {
        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());

        verify(studentService, never()).create(any(), any(), any(), any());
    }

    // --- behaviour ---

    @Test
    void authenticatedParentCanCreateAStudent() throws Exception {
        Student created = student(1L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(studentService.create(eq(SUBJECT), eq("Aarav"), eq("YEAR_5"), eq("SELECTIVE_MATHEMATICS")))
                .thenReturn(Optional.of(created));

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Aarav"))
                .andExpect(jsonPath("$.schoolYear").value("YEAR_5"))
                .andExpect(jsonPath("$.preparationGoal").value("SELECTIVE_MATHEMATICS"));
    }

    @Test
    void responseDoesNotExposeParentOwnershipIdentifiers() throws Exception {
        Student created = student(1L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(studentService.create(eq(SUBJECT), any(), any(), any())).thenReturn(Optional.of(created));

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentAccountId").doesNotExist())
                .andExpect(jsonPath("$.externalSubject").doesNotExist())
                .andExpect(jsonPath("$.sub").doesNotExist())
                .andExpect(jsonPath("$.parentEmail").doesNotExist());
    }

    @Test
    void ownershipIsDerivedOnlyFromTheJwtSubject() throws Exception {
        Student created = student(1L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(studentService.create(eq(SUBJECT), any(), any(), any())).thenReturn(Optional.of(created));

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(studentService).create(subjectCaptor.capture(), eq("Aarav"), eq("YEAR_5"), eq("SELECTIVE_MATHEMATICS"));
        assertThat(subjectCaptor.getValue()).isEqualTo(SUBJECT);
    }

    // --- ownership tampering ---

    @Test
    void requestCannotNominateAnotherParentViaTheRequestBody() throws Exception {
        Student created = student(1L, "Aarav", SchoolYear.YEAR_5, PreparationGoal.SELECTIVE_MATHEMATICS);
        when(studentService.create(eq(SUBJECT), eq("Aarav"), eq("YEAR_5"), eq("SELECTIVE_MATHEMATICS")))
                .thenReturn(Optional.of(created));

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Aarav",
                                  "schoolYear": "YEAR_5",
                                  "preparationGoal": "SELECTIVE_MATHEMATICS",
                                  "parentId": 999999,
                                  "parentAccountId": 999999,
                                  "ownerId": 999999,
                                  "externalSubject": "attacker-controlled",
                                  "subject": "attacker-controlled"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(studentService).create(subjectCaptor.capture(), eq("Aarav"), eq("YEAR_5"), eq("SELECTIVE_MATHEMATICS"));
        assertThat(subjectCaptor.getValue()).isEqualTo(SUBJECT);
    }

    // --- validation ---

    @Test
    void blankFirstNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "   ", "schoolYear": "YEAR_5", "preparationGoal": "SELECTIVE_MATHEMATICS" }
                                """))
                .andExpect(status().isBadRequest());

        verify(studentService, never()).create(any(), any(), any(), any());
    }

    @Test
    void missingSchoolYearReturnsBadRequest() throws Exception {
        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Aarav", "preparationGoal": "SELECTIVE_MATHEMATICS" }
                                """))
                .andExpect(status().isBadRequest());

        verify(studentService, never()).create(any(), any(), any(), any());
    }

    @Test
    void missingPreparationGoalReturnsBadRequest() throws Exception {
        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Aarav", "schoolYear": "YEAR_5" }
                                """))
                .andExpect(status().isBadRequest());

        verify(studentService, never()).create(any(), any(), any(), any());
    }

    @Test
    void unsupportedSchoolYearReturnsBadRequest() throws Exception {
        when(studentService.create(eq(SUBJECT), eq("Aarav"), eq("YEAR_9"), eq("SELECTIVE_MATHEMATICS")))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported school year."));

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Aarav", "schoolYear": "YEAR_9", "preparationGoal": "SELECTIVE_MATHEMATICS" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedPreparationGoalReturnsBadRequest() throws Exception {
        when(studentService.create(eq(SUBJECT), eq("Aarav"), eq("YEAR_5"), eq("UNKNOWN_GOAL")))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported preparation goal."));

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Aarav", "schoolYear": "YEAR_5", "preparationGoal": "UNKNOWN_GOAL" }
                                """))
                .andExpect(status().isBadRequest());
    }

    // --- duplicate student ---

    @Test
    void duplicateStudentForTheSameParentReturnsConflict() throws Exception {
        when(studentService.create(eq(SUBJECT), eq("Aarav"), eq("YEAR_5"), eq("SELECTIVE_MATHEMATICS")))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "A student with this name, school year and preparation goal already exists for this parent."));

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isConflict());
    }

    // --- missing parent account ---

    @Test
    void missingFutureMindsParentAccountReturnsNotFound() throws Exception {
        when(studentService.create(eq(SUBJECT), eq("Aarav"), eq("YEAR_5"), eq("SELECTIVE_MATHEMATICS")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(POST_STUDENTS_URI).with(jwt()
                        .jwt(builder -> builder.subject(SUBJECT))
                        .authorities(new SimpleGrantedAuthority("ROLE_PARENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound());
    }

    private static String validBody() {
        return """
                { "firstName": "Aarav", "schoolYear": "YEAR_5", "preparationGoal": "SELECTIVE_MATHEMATICS" }
                """;
    }

    /**
     * id/createdAt/updatedAt are DB-generated (no public setter by design) -
     * this test-only helper stands in for what a real persisted-then-reloaded
     * row would have, so response serialisation can be asserted without
     * weakening Student's immutability.
     */
    private static Student student(Long id, String firstName,
                                    SchoolYear schoolYear, PreparationGoal preparationGoal) {
        Student student = new Student(1L, firstName, schoolYear, preparationGoal);
        setField(student, "id", id);
        return student;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
