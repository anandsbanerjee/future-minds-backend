package au.com.futureminds.learning.platform.persistence.parentaccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentAccountRepository extends JpaRepository<ParentAccount, Long> {

    Optional<ParentAccount> findByExternalSubject(String externalSubject);
}
