package au.com.futureminds.learning.platform.api.student;

import au.com.futureminds.learning.platform.persistence.student.Student;

public record StudentResponse(
        Long id,
        String firstName,
        String schoolYear,
        String preparationGoal
) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getFirstName(),
                student.getSchoolYear().name(),
                student.getPreparationGoal().name());
    }
}
