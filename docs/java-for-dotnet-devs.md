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

## JPA Entities and Repositories

### `@Entity` and `@Table`

```java
@Entity
@Table(name = "users")
public class User { ... }
```

`@Entity` registers the class with JPA's persistence context. `@Table(name=...)` maps it to the exact DB table name. C# equivalent: an EF Core model class with `[Table("users")]`. Spring Boot's default naming converts camelCase to snake_case automatically, but being explicit avoids surprises.

### Primary keys — `@Id` and `@GeneratedValue`

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(updatable = false, nullable = false)
private UUID id;
```

`GenerationType.UUID` delegates to the DB's `gen_random_uuid()`. C# equivalent: `[Key]` + `.ValueGeneratedOnAdd()` in EF Core.

### Timestamp lifecycle — `@PrePersist` / `@PreUpdate`

```java
@PrePersist
protected void onCreate() { createdAt = updatedAt = OffsetDateTime.now(); }

@PreUpdate
protected void onUpdate() { updatedAt = OffsetDateTime.now(); }
```

Lifecycle callbacks that fire just before `INSERT` and `UPDATE`. C# equivalent: overriding `SaveChanges()` in `DbContext`, or using an EF Core `ISaveChangesInterceptor`.

### Relationships — `@OneToMany` / `@ManyToOne`

```java
// Inverse side (parent) — no FK column here
@OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<RecipeIngredient> ingredients = new ArrayList<>();

// Owning side (child) — holds the FK column
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "recipe_id", nullable = false)
private Recipe recipe;
```

The side with `@JoinColumn` owns the FK column in the DB. The `@OneToMany` side uses `mappedBy` to point back to the owning field name. C# equivalent: `HasMany(...).WithOne(...).HasForeignKey(...)` in `OnModelCreating`.

**Always use `FetchType.LAZY` on collections.** `EAGER` (the JPA default for `@ManyToOne`) loads the related object immediately; on `@OneToMany` it would load the entire collection on every parent query, causing N+1 problems.

### Composite primary keys — `@Embeddable` + `@EmbeddedId`

```java
@Embeddable
public class HouseholdMemberId implements Serializable {
    @Column(name = "household_id") private UUID householdId;
    @Column(name = "user_id")      private UUID userId;
    // equals() and hashCode() required
}

@Entity
public class HouseholdMember {
    @EmbeddedId private HouseholdMemberId id;

    @ManyToOne @MapsId("householdId") @JoinColumn(name = "household_id")
    private Household household;
}
```

`@Embeddable` is a value type embedded inside an entity — used here as the composite PK. `@EmbeddedId` marks the field that holds it. `@MapsId("householdId")` links the `@ManyToOne` FK to the matching field in the embedded key, preventing duplicate column mappings.

`equals()` and `hashCode()` are **required** on the embeddable — JPA uses them to identify and deduplicate cached entity instances.

C# equivalent: `HasKey(x => new { x.HouseholdId, x.UserId })` in `OnModelCreating`. Java requires the separate key class; C# does not.

### PostgreSQL arrays — `String[]`

```java
@Column(name = "occasions", columnDefinition = "text[]")
private String[] occasions;
```

Hibernate 6 (Spring Boot 3) maps `String[]` to PostgreSQL `text[]` natively. `columnDefinition` passes the raw SQL type hint to the JDBC driver. C# equivalent: a `string[]` property with `.HasConversion<string>()` or a custom `ValueConverter` in EF Core.

### Spring Data repositories

```java
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    List<Recipe> findByHouseholdId(UUID householdId);
    Optional<Recipe> findByIdAndHouseholdId(UUID id, UUID householdId);
}
```

Spring Data parses method names into JPQL at startup — no implementation needed. Rules:
- `findBy<Field>` → `WHERE field = ?`
- `findBy<Field>And<Field>` → `WHERE field1 = ? AND field2 = ?`
- `findBy<Field>OrderBy<OtherField>Asc` → adds `ORDER BY`
- Nested fields: `findByRecipe_Id(...)` traverses the `recipe` relationship to its `id` field

C# equivalent: LINQ expressions on `DbSet<T>` — same idea, different syntax.

### `@DataJpaTest` with Testcontainers

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class Phase1EntityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        // ...
    }
}
```

`@DataJpaTest` loads only the JPA slice (no web layer, no security). `replace = NONE` stops Spring from substituting your datasource with H2 — necessary when using PostgreSQL-specific types. `@DynamicPropertySource` injects the live container's connection details before the context starts. C# equivalent: `WebApplicationFactory` with `ConfigureWebHost` to swap the connection string.

Each test runs inside a transaction that rolls back automatically after the test, so no data leaks between tests.

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

## Database Migrations — Flyway

### Flyway vs EF Core migrations

| Concern | EF Core | Flyway |
|---|---|---|
| Migration files | C# classes, auto-generated | Plain `.sql` files, hand-written |
| Apply on startup | `app.MigrateAsync()` | Automatic when `spring.flyway.enabled=true` |
| Rollback | Manual down migrations | No built-in rollback — write a new migration |
| History table | `__EFMigrationsHistory` | `flyway_schema_history` |
| Checksum | None | SHA-256 of the SQL file — edit after apply = startup error |

Flyway is intentionally simple: it scans a directory, finds files it hasn't applied yet, and runs them in version order. No ORM knowledge required.

### Naming convention

```
V{version}__{description}.sql
```

- `V` prefix is required
- Version is a number (can use dots for sub-versions: `V1.1`)
- Two underscores separate the version from the description
- Description uses underscores as word separators

Examples: `V1__users_and_households.sql`, `V2__recipes.sql`, `V3__add_pantry_tables.sql`.

### Configuration

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration   # default; explicit here for clarity
```

In `application.yml`, this enables Flyway globally. In tests, `@DynamicPropertySource` can override `spring.flyway.enabled` to `true` and `spring.jpa.hibernate.ddl-auto` to `validate` — Flyway creates the schema, Hibernate only checks it.

### How the checksum enforcement works

On first apply, Flyway stores a SHA-256 checksum of each migration file in `flyway_schema_history`. On every subsequent startup it re-computes the checksum and compares. If a file was edited after it was applied, the app fails to start with:

```
FlywayException: Validate failed: Migration checksum mismatch for migration version 1
```

**Never edit an applied migration.** Write a new one instead. This is stricter than EF Core, which doesn't track file contents.

### Interaction with `ddl-auto`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

`validate` means Hibernate checks that its entity mappings match the live schema — it doesn't create or alter tables. This is the correct setting when Flyway owns the schema. The two work together: Flyway runs first (during datasource initialization), then Hibernate validates the result.

`create-drop` (sometimes used in early dev) lets Hibernate create the schema from entities and drop it on shutdown — bypass Flyway entirely. **Don't use `create-drop` once Flyway migrations exist.**

---

## Controllers and HTTP routing

### `@RestController` and `@RequestMapping`

```java
@RestController
@RequestMapping("/households")
public class HouseholdController { ... }
```

`@RestController` = `@Controller` + `@ResponseBody` — every method return value is serialized to JSON automatically. `@RequestMapping` on the class sets the base path. Method-level annotations narrow it further:

| Annotation | HTTP method | C# equivalent |
|---|---|---|
| `@GetMapping("/me")` | GET /households/me | `[HttpGet("me")]` |
| `@PostMapping` | POST /households | `[HttpPost]` |
| `@PatchMapping("/me")` | PATCH /households/me | `[HttpPatch("me")]` |
| `@DeleteMapping("/me/members/{userId}")` | DELETE /households/me/members/{id} | `[HttpDelete("me/members/{userId}")]` |

### `@RequestBody` and `@PathVariable`

```java
public ResponseEntity<HouseholdResponse> updateHousehold(
        @PathVariable UUID userId,
        @RequestBody UpdateHouseholdRequest request) { ... }
```

`@RequestBody` deserializes the JSON request body into the parameter type. C# equivalent: `[FromBody]`.

`@PathVariable` binds a URL path segment — `{userId}` in the route — to the parameter. C# equivalent: `[FromRoute]`. Spring automatically converts the string to `UUID`.

### `ResponseEntity<T>`

```java
return ResponseEntity.status(HttpStatus.CREATED).body(response);  // 201
return ResponseEntity.ok(response);                                // 200
return ResponseEntity.noContent().build();                         // 204 — no body
```

`ResponseEntity<T>` is `ActionResult<T>` — it lets you control the HTTP status code explicitly. When status code doesn't matter, you can return `T` directly from a `@RestController` method and Spring defaults to 200.

### `@AuthenticationPrincipal`

```java
@GetMapping("/me")
public ResponseEntity<HouseholdResponse> getMyHousehold(
        @AuthenticationPrincipal UserPrincipal principal) { ... }
```

Injects the object stored as the principal in `SecurityContextHolder` — populated by `JwtAuthFilter` on every authenticated request. C# equivalent: `HttpContext.User`, but typed. No string key lookups like `User.FindFirst("sub").Value` — you get `principal.userId()` directly, compile-time safe.

---

## HTTP Client — `RestTemplate`

```java
// GET with a typed response
ResponseEntity<GoogleUserInfo> resp =
    restTemplate.exchange(url, HttpMethod.GET,
        new HttpEntity<>(headers), GoogleUserInfo.class);
GoogleUserInfo info = resp.getBody();

// POST with a form-encoded body
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
body.add("grant_type", "authorization_code");
body.add("code", code);
restTemplate.postForEntity(url, new HttpEntity<>(body, headers), MyResponse.class);
```

`RestTemplate` is Spring's synchronous HTTP client — equivalent to C#'s `HttpClient`. It's configured as a singleton `@Bean` so it's shared and thread-safe across the app. Declare it in a `@Configuration` class rather than creating it with `new` inside a service.

`MultiValueMap<String, String>` (backed by `LinkedMultiValueMap`) is the Spring type for form data — equivalent to `FormUrlEncodedContent` in C#.

---

## Redis — `StringRedisTemplate`

```java
// Inject
private final StringRedisTemplate redis;

// Write with TTL
redis.opsForValue().set("session:" + userId, refreshToken, Duration.ofDays(7));

// Read
String value = redis.opsForValue().get("session:" + userId);

// Delete
redis.delete("session:" + userId);
```

`StringRedisTemplate` is a pre-configured `RedisTemplate<String, String>` — both keys and values are `String`. Spring Boot auto-configures it when `spring-boot-starter-data-redis` is on the classpath and `spring.data.redis.url` is set.

`.opsForValue()` returns the operations interface for plain key-value pairs. Redis also supports Hashes (`.opsForHash()`), Lists (`.opsForList()`), and Sets — each with their own ops interface.

C# equivalent: `IConnectionMultiplexer` from StackExchange.Redis — `db.StringSet(key, value, TimeSpan.FromDays(7))`.

---

## Service layer

### Interface + implementation pattern

```java
// HouseholdService.java — interface
public interface HouseholdService {
    HouseholdResponse createHousehold(UUID userId, String name);
    ...
}

// HouseholdServiceImpl.java — implementation
@Service
@Transactional
public class HouseholdServiceImpl implements HouseholdService { ... }
```

C# equivalent: `IHouseholdService` registered as `builder.Services.AddScoped<IHouseholdService, HouseholdServiceImpl>()`. In Spring, `@Service` on the implementation is all you need — no registration call. The controller declares `HouseholdService` (the interface) as its constructor parameter; Spring resolves the `HouseholdServiceImpl` bean automatically because it's the only registered type that implements the interface.

### `@Transactional`

```java
@Service
@Transactional                          // all public methods get a transaction
public class HouseholdServiceImpl { 

    @Transactional(readOnly = true)     // overrides the class-level default for reads
    public HouseholdResponse getMyHousehold(UUID householdId) { ... }
}
```

`@Transactional` at class level wraps every public method in a DB transaction. `readOnly = true` is an optimisation hint to the DB and Hibernate — Hibernate skips dirty-checking on read-only transactions, which speeds up SELECT-heavy methods.

Spring uses a proxy to intercept the call — the annotation has no effect if the method is called from within the same class (`this.method()` bypasses the proxy). This matters for methods like `AuthServiceImpl.handleGoogleCallback()`, which performs multiple DB writes (user upsert, household lookup) that must commit or roll back together.

C# equivalent: `TransactionScope` or EF Core's `SaveChanges()`, but declarative — no boilerplate in every method.

---

## Testing — web layer

### `@WebMvcTest` vs `@SpringBootTest`

```java
@WebMvcTest(HouseholdController.class)
class HouseholdControllerTest { ... }
```

`@WebMvcTest` loads only the web layer: controllers, filters, `SecurityFilterChain`. No services, no repositories, no database. Much faster than `@SpringBootTest` (full context). Use it for controller tests where you mock the service.

C# equivalent: `WebApplicationFactory` with only the controller registered, services replaced by mocks.

### `@MockBean`

```java
@MockBean private HouseholdService householdService;
@MockBean private JwtService jwtService;   // needed by JwtAuthFilter
```

Replaces a real Spring bean with a Mockito mock and wires it into the context. C# equivalent: replacing a service registration with a mock in `ConfigureWebHost`. `@MockBean JwtService` is required because `SecurityConfig` loads `JwtAuthFilter`, which depends on it — even though tests never use a JWT.

Compare with `@Mock` (Mockito only, no Spring context) — use `@Mock` in pure unit tests with `@ExtendWith(MockitoExtension.class)`, and `@MockBean` in `@SpringBootTest` integration tests where you need the full context but want to stub one collaborator (e.g., the `RestTemplate` that calls Google's API in `AuthIntegrationTest`).

### Injecting `UserPrincipal` in MockMvc tests

```java
private static RequestPostProcessor withPrincipal(UUID userId, UUID householdId, String role) {
    UserPrincipal principal = new UserPrincipal(userId, householdId, role);
    Authentication auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(role)));
    return authentication(auth);  // from SecurityMockMvcRequestPostProcessors
}

mockMvc.perform(get("/households/me").with(withPrincipal(userId, householdId, "member")))
       .andExpect(status().isOk());
```

`SecurityMockMvcRequestPostProcessors.authentication(auth)` pre-populates the `SecurityContext` before MockMvc dispatches the request. `JwtAuthFilter` sees no `Authorization` header and passes through; `authorizeHttpRequests` finds the pre-set auth and allows it.

C# equivalent: setting `HttpContext.User = new ClaimsPrincipal(...)` in a test delegating handler.

---

## `ReflectionTestUtils` — Setting `@Value` fields in unit tests

```java
@BeforeEach
void setUp() {
    ReflectionTestUtils.setField(authService, "googleClientId", "test-client-id");
}
```

Spring's `@Value` fields are injected by the Spring context, but unit tests using `@InjectMocks` bypass Spring entirely. `ReflectionTestUtils.setField()` sets private fields via reflection so the unit under test behaves as if the value was injected.

C# equivalent: using `typeof(MyService).GetField("_field", BindingFlags.NonPublic | BindingFlags.Instance).SetValue(instance, "value")` — or redesigning to pass the value through the constructor.

---

## `@RequestParam` — query string parameters

```java
@GetMapping
public ResponseEntity<List<RecipeResponse>> listRecipes(
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "per_page", defaultValue = "20") int perPage) { ... }
```

`@RequestParam` binds HTTP query parameters (`?q=pasta&page=2&per_page=10`) to method arguments. C# equivalent: `[FromQuery]`.

| Attribute | Meaning | C# equivalent |
|---|---|---|
| `required = false` | param is optional; method receives `null` if absent | `string? q` |
| `defaultValue = "1"` | fallback when param is missing | `int page = 1` |
| `name = "per_page"` | maps `per_page` (URL) → `perPage` (Java param) | `[FromQuery(Name = "per_page")]` |

Spring automatically converts the string to the declared type — `int`, `BigDecimal`, `UUID`, etc.

---

## Spring Data JPA — custom queries with `@Query`

When the derived query naming convention can't express what you need (e.g. ILIKE, OR conditions), use `@Query` with JPQL:

```java
@Query("SELECT r FROM Recipe r " +
       "WHERE r.householdId = :householdId " +
       "AND (LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
       "     OR LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
Page<Recipe> findByHouseholdIdAndKeyword(
        @Param("householdId") UUID householdId,
        @Param("keyword") String keyword,
        Pageable pageable);
```

**JPQL operates on entity class names and field names, not table/column names.** `Recipe` is the class; `r.title` is the Java field — not `recipes.title`.

JPQL has no `ILIKE`. The workaround `LOWER(...) LIKE LOWER(...)` produces a case-insensitive match that works on any JDBC-compliant DB. If you need true PostgreSQL `ILIKE` for performance on large tables, switch to `nativeQuery = true` and write raw SQL.

Named parameters (`:keyword`) require `@Param("keyword")` on the method argument. The `Pageable` parameter is handled automatically and does not appear in the JPQL.

C# equivalent: LINQ `.Where(r => EF.Functions.ILike(r.Title, $"%{keyword}%") || ...)`.

---

## Spring Data JPA — pagination with `Pageable` and `Page<T>`

```java
// Repository — Spring Data derives a paginated query from the method name
Page<Recipe> findByHouseholdId(UUID householdId, Pageable pageable);

// Service — construct a Pageable and extract results
PageRequest pageable = PageRequest.of(page - 1, perPage);   // 0-based page index
Page<Recipe> results = recipeRepository.findByHouseholdId(householdId, pageable);
List<Recipe> content = results.getContent();                 // just the rows
long total = results.getTotalElements();                     // total matching rows across all pages
```

`PageRequest.of(index, size)` is the concrete `Pageable` implementation. **Page numbers are 0-based in Spring Data** — subtract 1 when your API uses 1-based pages.

`Page<T>` wraps the result slice with metadata:
| Method | Returns |
|---|---|
| `getContent()` | `List<T>` — the current page's rows |
| `getTotalElements()` | total rows across all pages |
| `getTotalPages()` | number of pages at current size |
| `getNumber()` | current page index (0-based) |

C# equivalent: `queryable.Skip((page - 1) * perPage).Take(perPage).ToList()` with a separate `.Count()` for totals — Spring wraps both into one object.

---

## `BigDecimal` — exact decimal arithmetic

Java's `BigDecimal` is the equivalent of C#'s `decimal`. The critical difference is that `BigDecimal.divide()` **requires an explicit scale and `RoundingMode`** — otherwise it throws `ArithmeticException` on non-terminating decimals (e.g. 1/3):

```java
// C#: decimal scaleFactor = requested / recipe.Servings;  (no extra args needed)

// Java — must specify scale (decimal places to keep) and how to round:
BigDecimal scaleFactor = requestedServings.divide(recipe.getServings(), 6, RoundingMode.HALF_UP);
BigDecimal scaled = ingredient.getQuantity()
        .multiply(scaleFactor)
        .setScale(2, RoundingMode.HALF_UP);   // round display value to 2 decimal places
```

`RoundingMode.HALF_UP` is the familiar "school rounding" (0.5 rounds up). Use it for displayed quantities. Use `RoundingMode.UNNECESSARY` only when you know the result is exact — it throws if rounding would be required.

**Never use `==` or `equals()` to compare two `BigDecimal` values for numeric equality** — `new BigDecimal("4")` and `new BigDecimal("4.0")` are `equals`-unequal because they have different scales. Use `.compareTo()` instead: `a.compareTo(b) == 0`.

---

## Mockito — `verify()` for asserting calls

`when(...).thenReturn(...)` stubs what a mock returns. `verify()` asserts that the mock was actually called with specific arguments — useful when the method return value is `void`, or when you want to confirm the right arguments reached the dependency:

```java
// Stub
when(recipeService.listRecipes(HOUSEHOLD_ID, "pasta", 1, 20))
        .thenReturn(List.of(recipeResponse("Garlic Pasta")));

// Act
mockMvc.perform(get("/recipes?q=pasta").with(asMember()))
        .andExpect(status().isOk());

// Assert the service was called with exactly these arguments
verify(recipeService).listRecipes(HOUSEHOLD_ID, "pasta", 1, 20);
```

`verify(mock)` asserts exactly one call. Use `verify(mock, times(2))` for two calls, `verify(mock, never())` to assert no call happened.

C# equivalent: `mock.Verify(s => s.ListRecipes(householdId, "pasta", 1, 20), Times.Once())`.

---

## Spring AMQP — publishing messages with `RabbitTemplate`

Spring AMQP (`spring-boot-starter-amqp`) is the Spring abstraction over RabbitMQ. The central class is `RabbitTemplate` — injected like any other bean and used to publish messages.

```java
// Publish a message to the default exchange, routing by queue name.
// RabbitTemplate serialises the object to JSON if Jackson2JsonMessageConverter is configured.
rabbitTemplate.convertAndSend("", "scrape.jobs", new ScrapeJobMessage(jobId, url, householdId));
//                             ^     ^               ^
//                             |     routing key     payload (serialised to JSON)
//                             exchange ("" = default exchange, routes by queue name)
```

C# equivalent: `IModel.BasicPublish("", "scrape.jobs", props, body)` in RabbitMQ.Client, or `IBus.Publish(message)` in MassTransit.

Queue and exchange topology is declared as `@Bean` methods in a `@Configuration` class. Spring AMQP's `RabbitAdmin` picks these up and creates the queues/exchanges on the broker at startup — no manual broker setup needed:

```java
@Bean
public Queue scrapeJobsQueue() {
    // durable() = survives broker restarts (equivalent to durable: true in amqplib)
    return QueueBuilder.durable("scrape.jobs")
            .withArgument("x-dead-letter-exchange", "scrape.jobs.dlx")
            .build();
}

@Bean
public DirectExchange deadLetterExchange() {
    return new DirectExchange("scrape.jobs.dlx");
}

@Bean
public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
    // BindingBuilder reads like a sentence: bind this queue to this exchange with this routing key.
    return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("scrape.jobs");
}
```

The message converter must be configured explicitly — by default `RabbitTemplate` uses Java serialisation, which is opaque to the Python consumer:

```java
@Bean
public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}

@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter());
    return template;
}
```

C# equivalent: configuring a custom serialiser in MassTransit (`UseJsonSerializer()`) or setting `ContentType = "application/json"` manually in RabbitMQ.Client.

---

## Redis — writing with TTL via `StringRedisTemplate`

`StringRedisTemplate` is a pre-configured `RedisTemplate<String, String>` — both key and value are plain strings. Use `opsForValue()` to get the string operations interface:

```java
// SET key value EX 604800  (7 days in seconds)
stringRedisTemplate.opsForValue().set(key, value, 7, TimeUnit.DAYS);

// GET key — returns null if the key doesn't exist or has expired
String stored = stringRedisTemplate.opsForValue().get(key);

// Check existence without fetching the value
Boolean exists = stringRedisTemplate.hasKey(key);
```

C# equivalent with StackExchange.Redis:
```csharp
db.StringSet(key, value, TimeSpan.FromDays(7));   // SET EX
string stored = db.StringGet(key);                 // GET
bool exists   = db.KeyExists(key);                 // EXISTS
```

The `TimeUnit` parameter mirrors the Redis `EX` / `PX` / `EXAT` options — `TimeUnit.DAYS`, `TimeUnit.HOURS`, `TimeUnit.SECONDS`, etc. This is a single atomic `SET key value EX ttl` call, not a `SET` followed by `EXPIRE`.

---

*This doc grows as new patterns appear during development.*

*This doc grows as new patterns appear during development.*
