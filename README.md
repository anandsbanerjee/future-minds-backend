# future-minds-backend
Backend codebase using Java / Spring Boot APIs, persistence, workflow and deterministic business logic

## Configuration & profiles

Configuration is defined with `.properties` files under `src/main/resources`:

- `application.properties` — base configuration common to all environments
- `application-local.properties` — local development
- `application-test.properties` — test execution
- `application-prod.properties` — production

No profile is active by default. The runtime environment selects it explicitly via
the `SPRING_PROFILES_ACTIVE` environment variable, for example:

```
SPRING_PROFILES_ACTIVE=local
SPRING_PROFILES_ACTIVE=test
SPRING_PROFILES_ACTIVE=prod
```

Secrets and credentials are never committed; environment-sensitive values are
supplied through environment variables.
