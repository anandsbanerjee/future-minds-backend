package au.com.futureminds.learning.platform.persistence.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoal(
            Long parentAccountId, String firstName, SchoolYear schoolYear, PreparationGoal preparationGoal);

    boolean existsByParentAccountIdAndFirstNameAndSchoolYearAndPreparationGoalAndIdNot(
            Long parentAccountId, String firstName, SchoolYear schoolYear, PreparationGoal preparationGoal, Long id);

    List<Student> findByParentAccountIdAndDeactivatedAtIsNullOrderByIdAsc(Long parentAccountId);

    Optional<Student> findByIdAndParentAccountId(Long id, Long parentAccountId);

    Optional<Student> findByIdAndParentAccountIdAndDeactivatedAtIsNull(Long id, Long parentAccountId);
}
