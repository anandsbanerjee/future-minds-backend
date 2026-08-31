package au.com.futureminds.learning;

import au.com.futureminds.learning.platform.persistence.parentaccount.ParentAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FutureMindsApplicationTests {

	// The "test" profile excludes DataSource/JPA autoconfiguration so this smoke
	// test does not require a live database; ParentAccountService needs a real
	// ParentAccountRepository, so it is mocked out here rather than constructed.
	@MockitoBean
	private ParentAccountService parentAccountService;

	@Test
	void contextLoads() {
	}

}
