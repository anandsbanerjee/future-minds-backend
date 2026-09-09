CREATE TABLE student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_account_id BIGINT NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    school_year VARCHAR(50) NOT NULL,
    preparation_goal VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_parent_account
        FOREIGN KEY (parent_account_id) REFERENCES parent_account (id)
);
