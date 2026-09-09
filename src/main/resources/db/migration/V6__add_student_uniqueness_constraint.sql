ALTER TABLE student
    ADD CONSTRAINT uq_student_parent_first_name_year_goal
        UNIQUE (parent_account_id, first_name, school_year, preparation_goal);
