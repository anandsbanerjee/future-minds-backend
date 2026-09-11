package au.com.futureminds.learning.platform.persistence.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_account_id", nullable = false)
    private Long parentAccountId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "school_year", nullable = false)
    @Enumerated(EnumType.STRING)
    private SchoolYear schoolYear;

    @Column(name = "preparation_goal", nullable = false)
    @Enumerated(EnumType.STRING)
    private PreparationGoal preparationGoal;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Student() {
    }

    public Student(Long parentAccountId, String firstName, SchoolYear schoolYear, PreparationGoal preparationGoal) {
        this.parentAccountId = parentAccountId;
        this.firstName = firstName;
        this.schoolYear = schoolYear;
        this.preparationGoal = preparationGoal;
    }

    /**
     * Application-owned profile edit, entirely separate from ownership/system
     * fields (id, parentAccountId, createdAt, updatedAt), which have no
     * mutator by design - see StudentService.updateForParent.
     */
    public boolean updateFirstName(String firstName) {
        if (Objects.equals(this.firstName, firstName)) {
            return false;
        }
        this.firstName = firstName;
        return true;
    }

    public boolean updateSchoolYear(SchoolYear schoolYear) {
        if (Objects.equals(this.schoolYear, schoolYear)) {
            return false;
        }
        this.schoolYear = schoolYear;
        return true;
    }

    public boolean updatePreparationGoal(PreparationGoal preparationGoal) {
        if (Objects.equals(this.preparationGoal, preparationGoal)) {
            return false;
        }
        this.preparationGoal = preparationGoal;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getParentAccountId() {
        return parentAccountId;
    }

    public String getFirstName() {
        return firstName;
    }

    public SchoolYear getSchoolYear() {
        return schoolYear;
    }

    public PreparationGoal getPreparationGoal() {
        return preparationGoal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
