package au.com.futureminds.learning.platform.api.system;

import au.com.futureminds.learning.platform.api.ApiPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse("UP", applicationName);
    }
}
