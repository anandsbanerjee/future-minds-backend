package au.com.futureminds.learning.platform.persistence.student;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal(
            Long parentAccountId, String firstName, SchoolYear schoolYear, PreparationGoal preparationGoal);
}
