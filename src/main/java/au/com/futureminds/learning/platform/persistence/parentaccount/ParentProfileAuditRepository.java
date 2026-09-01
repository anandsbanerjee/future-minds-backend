package au.com.futureminds.learning.platform.persistence.parentaccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentProfileAuditRepository extends JpaRepository<ParentProfileAudit, Long> {
}
