# Java / Spring Boot — Reference for .NET Developers

Concepts explained as you encounter them while building this project. Each entry shows the C# equivalent, what the Java code actually does, and any gotchas.

---

## Language features

### `record`

```java
public record ApiError(String error, int status) {}
```

Same concept as a C# `record struct` — immutable, compiler-generates constructor, `equals`, `hashCode`, and `toString`. The empty `{}` body means no custom logic; add methods there if needed.

The compiler expands it to roughly:

```java
public final class ApiError {
    private final String error;
    private final int status;

    public ApiError(String error, int status) {
        this.error = error;
        this.status = status;
    }

    public String error() { return error; }  // no "get" prefix — this is intentional
    public int status()   { return status; }

    // equals, hashCode, toString auto-generated
}
```

**Accessor naming gotcha:** Record accessors use `error()` not `getError()`. Jackson handles this natively (Jackson 2.12+), so JSON serialization works without extra config.

---

## Spring annotations

Annotations in Spring are the equivalent of ASP.NET Core attributes + the DI registration you'd write in `Program.cs` — but instead of explicit `services.Add*()` calls, Spring scans the classpath and wires things automatically based on which annotation is present.

### `@SpringBootApplication`

```java
@SpringBootApplication
public class RecipeManagerApplication { ... }
```

Three annotations in one: `@Configuration` (this class defines beans), `@EnableAutoConfiguration` (Spring Boot auto-configures based on what's on the classpath), and `@ComponentScan` (scan this package and sub-packages for components to register). C# equivalent: the entirety of `Program.cs` setup — `builder.Services.Add*()`, `app.Use*()`, and `app.Run()`.

### `@RestController`

```java
@RestController
@RequestMapping("/recipes")
public class RecipeController { ... }
```

C# equivalent: `[ApiController]` + `[Route("recipes")]`. Every method return value is serialized to JSON automatically (no need for `Ok()` wrappers, though you can use `ResponseEntity` when you need to control the status code).

### `@Service` / `@Repository`

```java
@Service
public class RecipeServiceImpl implements RecipeService { ... }

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> { ... }
```

C# equivalent: `services.AddScoped<IRecipeService, RecipeServiceImpl>()` — but you don't write the registration. `@Service` marks a class for Spring to discover and manage. `@Repository` does the same and additionally translates database exceptions into Spring's exception hierarchy.

`JpaRepository<Recipe, UUID>` is like inheriting from EF Core's `DbSet<T>` — Spring Data generates the implementation (findById, save, delete, etc.) at runtime from the interface alone.

### `@RestControllerAdvice`

```java
@RestControllerAdvice
public class GlobalExceptionHandler { ... }
```

C# equivalent: `IExceptionFilter` or a middleware that catches unhandled exceptions. Any `@ExceptionHandler` method inside this class intercepts exceptions thrown from any controller in the application. See `GlobalExceptionHandler.java` for usage.

### `@ExceptionHandler`

```java
@ExceptionHandler(EntityNotFoundException.class)
public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError(ex.getMessage(), 404));
}
```

Binds a method to a specific exception type. When that exception propagates out of any controller, Spring calls this method instead of returning a 500. C# equivalent: checking `context.Exception.GetType()` inside an `IExceptionFilter`.

`ResponseEntity<T>` = `ActionResult<T>` — it wraps a response body with an HTTP status code.

---

## Project structure

### `pom.xml` vs `.csproj`

`pom.xml` is Maven's project descriptor — equivalent to a `.csproj` file. It defines:
- **`<parent>`** — inherits managed dependency versions from `spring-boot-starter-parent` (like a global `Directory.Packages.props`)
- **`<dependencies>`** — NuGet packages equivalent; no version needed for Spring Boot starters because the parent BOM manages them
- **`<build><plugins>`** — MSBuild targets equivalent; the `spring-boot-maven-plugin` produces a fat JAR (all dependencies bundled), the way `dotnet publish` produces a self-contained executable

### Package structure

```
com.recipemanager.api/
├── controller/      # @RestController classes — HTTP boundary, thin layer
├── service/         # Interfaces (IRecipeService pattern)
│   └── impl/        # @Service implementations
├── repository/      # @Repository interfaces extending JpaRepository
├── domain/          # @Entity classes — JPA-mapped DB tables
├── dto/             # Request/response shapes (records or plain classes)
├── config/          # @Configuration classes — Spring beans, security config
└── exception/       # ApiException, GlobalExceptionHandler, ApiError
```

Java convention: package names are all lowercase, dot-separated, reverse-domain prefix (`com.recipemanager.api`). No namespaces in the C# sense — the package name in each file must match its directory path.

### `application.yml` vs `appsettings.json`

`application.yml` is the primary config file. Spring reads environment variables and maps them to properties using a relaxed binding convention:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/recipe_manager}
```

`${VAR:default}` means: read env var `VAR`, fall back to `default` if not set. C# equivalent: `builder.Configuration["ConnectionStrings:Default"] ?? "fallback"`. Custom properties (like `jwt.secret`) are injected into `@ConfigurationProperties` classes — the equivalent of the options pattern (`IOptions<T>`).

---

## Testing

### `@SpringBootTest` vs `WebApplicationFactory<T>`

```java
@SpringBootTest
class RecipeControllerTest { ... }
```

Loads the full Spring application context. Direct equivalent of `WebApplicationFactory<Program>` in ASP.NET Core integration tests.

### MockMvc vs `HttpClient` from test factory

```java
@Autowired MockMvc mockMvc;

mockMvc.perform(get("/api/v1/recipes"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.length()").value(3));
```

`MockMvc` drives HTTP requests through the full controller stack without starting a real server. C# equivalent: `factory.CreateClient()` then `client.GetAsync(...)`, but assertions are fluent and inline rather than on the response object.

### `@Transactional` on tests

```java
@Test
@Transactional
void createRecipe_persistsToDatabase() { ... }
```

Wraps the test in a transaction and rolls it back after — no test data leaks between tests. C# equivalent: manually calling `context.Database.BeginTransaction()` and `transaction.Rollback()` in a teardown, but here it's one annotation.

---

## Spring Security

### `SecurityFilterChain` — the central security configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2.loginPage("/auth/google"));
        return http.build();
    }
}
```

**`@Configuration` + `@EnableWebSecurity`**

`@Configuration` marks this class as a source of `@Bean` methods for the DI container. `@EnableWebSecurity` activates Spring Security's filter chain. C# equivalent: `builder.Services.AddAuthentication(...) + AddAuthorization(...)` in `Program.cs`, except the setup lives in a dedicated class.

**`SecurityFilterChain`**

A chain of servlet filters that runs on every HTTP request before it reaches your controllers — same concept as ASP.NET Core middleware. Without a custom bean, Spring Boot defaults to redirecting all unauthenticated traffic to OAuth2 login (which is what we saw when `/actuator/health` returned a 302 to Google).

**`.csrf(csrf -> csrf.disable())`**

CSRF attacks exploit session cookies. We use stateless JWTs — no server-side session to hijack — so disabling CSRF is correct. ASP.NET Core does the same automatically when JWT bearer auth is configured.

**`.sessionManagement(...STATELESS)`**

Tells Spring Security never to create an `HttpSession`. Without this, Spring would still issue `JSESSIONID` cookies even though we're fully stateless. Every request must carry identity in the `Authorization` header.

**`.authorizeHttpRequests(...)`**

The route access policy — equivalent to `[Authorize]` attributes, but defined centrally. Rules evaluate top-to-bottom; first match wins:

| Matcher | Policy |
|---|---|
| `/actuator/health`, `/actuator/info` | `permitAll()` — public, no token needed |
| `/auth/**` | `permitAll()` — login flow; callers have no token yet |
| `anyRequest()` | `authenticated()` — requires a valid JWT |

**`.oauth2Login(...)`**

Tells Spring where to send unauthenticated requests that hit a protected route. The current value (`/auth/google`) is a stub — EP1-05 and EP1-06 replace this with our own JWT issuance flow.

### Why two phases? (Google OAuth2 vs our JWTs)

Spring's OAuth2 client handles the Google handshake: redirect → consent → exchange code for Google profile. That proves identity once. After that, we issue our own short-lived JWT (15 min access + 7 day refresh stored in Redis). From that point forward, our JWTs are the session credential — Google is never contacted again.

EP1-05 adds the `JwtAuthFilter` (`OncePerRequestFilter`) that intercepts every request, validates our JWT's signature and expiry via jjwt, and populates `SecurityContextHolder` with a `UserPrincipal`. That's when `SecurityConfig` gets the full treatment.

---

*This doc grows as new patterns appear during development.*
