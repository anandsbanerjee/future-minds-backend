package au.com.futureminds.learning.platform.persistence.parentaccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParentConsentRepository extends JpaRepository<ParentConsent, Long> {

    List<ParentConsent> findByParentAccountIdOrderByRecordedAtDesc(Long parentAccountId);
}
