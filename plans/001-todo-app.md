# Plan 001: Build a single-user todo web app on Spring Boot 4.2, PostgreSQL and Thymeleaf, tested against real PostgreSQL via Testcontainers

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index. If you were dispatched by a reviewer, or the index lives
> in a checkout you cannot write to, skip it and say so in your report.
>
> **Drift check (run first)**: this is a greenfield plan. Run `ls -A` in the
> repository root. Expected: only `.git` and `plans` (plus `.claude` if
> present). If **any** other file or directory exists (`pom.xml`, `src/`,
> `mvnw`, ...), someone has started the project since this plan was written —
> STOP and report what you found.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: direction
- **Planned at**: no commit — `/home/jpinheiro/blackshield/sample_project` was an empty directory and not a git repository, 2026-11-23
- **Premise verified**: pending

## Why this matters

The repository is empty; the owner wants a todo list web application built on
Java with Spring Boot 4, PostgreSQL as the only database, Thymeleaf
server-side templates, and Testcontainers wherever a test needs a database.
Agreed scope: **single user, no login**. A todo has a title, a done flag and
a creation time. Users can add, rename, toggle done/undone, delete, filter
(all / active / completed) and clear all completed todos. Spring Boot 4
reorganised many starters and test-annotation packages compared to Boot 3, so
everything Boot-4-specific in this plan (artifact names, import paths,
Testcontainers 2 class names) was **compiled and run on a scratch build on
2026-11-23** — copy it exactly instead of following Boot 3 tutorials.

CSRF protection is in scope even without login: every state change is an HTML
form POST, and without CSRF tokens any website the user visits could submit
those forms to the app (for example, wipe the list via
`/todos/clear-completed`). Spring Security is added with CSRF on and all
requests permitted.

## Current state

- The repository root contains only `plans/` (this file and `plans/README.md`).
  No code, no build file.
- Toolchain observed on the planning machine on 2026-11-23:
  - JDK: `openjdk version "25.0.5.1"` (Java 25, an LTS release) — the project targets Java 25.
  - Maven 3.9.12 is installed, but the project uses the Maven wrapper `./mvnw`,
    which downloads Maven 3.10.1 by itself. Always use `./mvnw`, never `mvn`.
  - Docker 29.9.3 is running. Testcontainers needs a working Docker daemon.
  - Network access to `start.spring.io` and Maven Central is required.
- Versions resolved by Spring Initializr / Maven Central on 2026-11-23:
  Spring Boot **4.2.0** (latest GA), Testcontainers **2.0.x** (managed by Boot,
  do not pin it yourself), Flyway (managed by Boot). The PostgreSQL test image
  is pinned to `postgres:18-alpine` (18 is mature; 19.0 was only just released).
- Boot 4 facts this plan depends on (all verified by compiling and running
  them on 2026-11-23 — if any of them does not resolve for you, see STOP conditions):
  - Web starter is `spring-boot-starter-webmvc` (not `-web`); every starter has
    a matching `-test` starter (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, ...).
  - Flyway needs `spring-boot-starter-flyway` **plus** `org.flywaydb:flyway-database-postgresql`.
  - Testcontainers 2 artifacts are `org.testcontainers:testcontainers-postgresql`
    and `org.testcontainers:testcontainers-junit-jupiter`; the container class is
    `org.testcontainers.postgresql.PostgreSQLContainer` (no generic type parameter).
  - Test annotation packages:
    - `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
    - `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`
    - `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  - `@ServiceConnection` (from `org.springframework.boot.testcontainers.service.connection`)
    wires the container's JDBC URL/user/password into the app — no
    `@DynamicPropertySource` needed.
  - Spring Security auto-creates an in-memory user and logs
    `Using generated security password: ...` unless
    `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`
    is excluded. The app has no users, so it is excluded.
  - Thymeleaf inserts the hidden `_csrf` input automatically into every form
    whose action is written as `th:action="@{...}"` (not a plain `action="..."`).
    `thymeleaf-extras-springsecurity6` is **not** needed for this.
- Conventions to follow (there is no existing code; these are the conventions
  Spring Initializr generates — keep them): tabs for indentation in Java,
  constructor injection, `@Configuration(proxyBeanMethods = false)`
  (`SecurityConfig` is package-private; `TodoService` and `TodoController` are
  `public` on purpose — keep every file exactly as given), tests named `*Tests` (surefire
  picks up `*Tests.java`; do **not** name tests `*IT`, those would silently not
  run because no failsafe plugin is configured).

### Design (what you will build)

| Concern | Decision |
|---|---|
| Package | `com.example.todo`, flat (one package — the app is ~10 classes) |
| Schema | Flyway migration `V1__create_todo.sql`; Hibernate `ddl-auto=validate` (Hibernate never creates/alters tables) |
| Title rules | trimmed; 1–200 chars after trimming; enforced by Bean Validation in the form **and** by the DB (`VARCHAR(200)` + `CHECK`) |
| Ordering | creation time ascending, then id |
| Unknown id | `TodoNotFoundException` annotated `@ResponseStatus(NOT_FOUND)` → HTTP 404 with `templates/error/404.html` |
| Bad `filter` value | Spring's enum conversion fails → HTTP 400 (default behaviour, tested) |
| HTTP shape | Only GET and POST (HTML forms cannot send PUT/DELETE; no hidden-method filter). Post/Redirect/Get after every successful POST; on validation errors the page is re-rendered with HTTP 200 and field errors |
| Filter preserved | Every list-page POST carries `?filter=` and redirects back to `/todos?filter=<FILTER>` |
| Security | Spring Security, CSRF on, `anyRequest().permitAll()`, no login, no users |
| Open-session-in-view | disabled (`spring.jpa.open-in-view=false`) — all entity access happens inside `TodoService` transactions |

Endpoints:

| Method | Path | Behaviour |
|---|---|---|
| GET | `/` | redirect to `/todos` |
| GET | `/todos?filter=ALL\|ACTIVE\|COMPLETED` | list page (default `ALL`) |
| POST | `/todos?filter=...` | create from form field `title` |
| POST | `/todos/{id}/toggle?filter=...` | flip done flag |
| POST | `/todos/{id}/delete?filter=...` | delete |
| POST | `/todos/clear-completed?filter=...` | delete all completed |
| GET | `/todos/{id}/edit` | edit page |
| POST | `/todos/{id}` | rename from form field `title`, redirect to `/todos` |

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile incl. tests | `./mvnw -q -B test-compile` | exit 0, no output |
| One test class | `./mvnw -B test -Dtest=TodoWebTests \| grep -E 'Tests run: [0-9]+, Fail.*Skipped: [0-9]+$\|BUILD'` | `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` |
| One test method | `./mvnw -B test -Dtest='TodoWebTests#titleIsHtmlEscaped'` | `Tests run: 1, Failures: 0, Errors: 0` |
| Full suite | `./mvnw -B verify` | `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` |
| Run app for development (throwaway Postgres in Docker via Testcontainers) | `./mvnw spring-boot:test-run` | log line `Tomcat started on port 8080` — use the exact recipe in Step 5.2 when running it as a check |

The first `./mvnw` run downloads Maven and all dependencies, and the first test
run pulls `postgres:18-alpine`; allow a few minutes. Every test class starts a
PostgreSQL container, so Docker must be running for **every** test command.
Pipe long output through `| grep -E 'Tests run: [0-9]+, Fail.*Skipped: [0-9]+$|BUILD'` (this anchored pattern prints only the summary line; the unanchored `'Tests run:'` would also print one line per test class).

There is no linter or formatter configured; none is added by this plan.

## Suggested executor toolkit

No Spring/Java-specific skill was available while planning. Reference docs, if
something in this plan does not resolve:

- Spring Boot 4.2 reference — Testcontainers: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- Spring Boot 4.0 migration guide (starter and package renames): https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide

## Scope

**In scope** (the only files you should create; all paths relative to the repository root):

- Generated by Spring Initializr in Step 1 (keep): `pom.xml`, `mvnw`, `mvnw.cmd`,
  `.mvn/wrapper/maven-wrapper.properties`, `.gitignore`, `.gitattributes`,
  `src/main/java/com/example/todo/TodoApplication.java`,
  `src/test/java/com/example/todo/TestTodoApplication.java`,
  `src/test/java/com/example/todo/TodoApplicationTests.java`
- Generated, then edited: `src/test/java/com/example/todo/TestcontainersConfiguration.java`,
  `src/main/resources/application.properties`
- Created by you:
  - `src/main/resources/db/migration/V1__create_todo.sql`
  - `src/main/java/com/example/todo/{Todo,TodoRepository,TodoFilter,TodoNotFoundException,TodoService,TodoForm,TodoController,SecurityConfig}.java`
  - `src/main/resources/templates/todos/list.html`, `src/main/resources/templates/todos/edit.html`, `src/main/resources/templates/error/404.html`
  - `src/test/java/com/example/todo/TodoRepositoryTests.java`, `src/test/java/com/example/todo/TodoWebTests.java`
  - `README.md`
- `plans/README.md` — status row only.

**Out of scope** (do NOT add, even though they look related):

- Login, users, roles, `thymeleaf-extras-springsecurity6` — scope is single-user, no auth.
- A JSON/REST API, JavaScript, CSS frameworks, htmx — server-rendered HTML forms only.
- `spring-boot-docker-compose`, `compose.yaml`, Dockerfile, CI config — not requested.
- Due dates, priorities, tags, pagination, drag-and-drop ordering.
- Spotless/Checkstyle or any other plugin in `pom.xml` beyond what Initializr generates.
- `HELP.md` — delete it (Initializr boilerplate).
- Any change to `plans/001-*.md` (this file).

## Git workflow

- Branch: `advisor/001-todo-app`.
- There is no commit history to copy a message style from. Use short imperative
  messages, one commit per step, e.g. `Add Spring Boot 4.2 project skeleton`,
  `Add todo persistence with Flyway schema`, `Add Thymeleaf todo UI with CSRF protection`,
  `Add README`.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 0: Preconditions

Run each and compare:

- `java -version 2>&1 | head -1` → contains `"25.` (Java 25). If it shows any other major version, STOP — do **not** lower `javaVersion` / `<java.version>`.
- `docker info --format '{{.ServerVersion}}'` → prints a version, exit 0. Otherwise STOP.
- `git rev-parse --is-inside-work-tree` → `true`. Otherwise STOP (the operator must `git init` and commit `plans/` first).
- `git config user.name && git config user.email` → both print a value. Otherwise STOP and ask the operator; do not invent an identity.
- `curl -s -o /dev/null localhost:8080; echo $?` → `7` (nothing listening on port 8080). If it prints `0`, something already uses the port: STOP before Step 5.2 and report it.
- Branch: if you were dispatched into a dedicated worktree/branch (e.g. by `coding:execute-plan`), stay on it. Otherwise run `git checkout -b advisor/001-todo-app`.

**Verify**: all of the above as stated.

### Step 1: Generate the project skeleton

Run from the repository root (the `curl` is a single command; it downloads the
skeleton to a temp dir, never into the repo):

```bash
TMP=$(mktemp -d) && curl -sf https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=4.2.0 -d javaVersion=25 \
  -d groupId=com.example -d artifactId=todo -d name=todo -d packageName=com.example.todo \
  -d dependencies=web,thymeleaf,data-jpa,postgresql,flyway,testcontainers,validation,security \
  -o "$TMP/todo.zip" && unzip -q "$TMP/todo.zip" -d "$TMP/todo" && cp -a "$TMP/todo/." . && rm -rf "$TMP" && rm HELP.md
```

If that command exits non-zero (network failure, or Initializr rejecting
`bootVersion=4.2.0`), STOP and report — do not hand-write the project.

Then make two edits.

**Edit 1** — in `pom.xml`, delete this dependency block (it is not needed;
CSRF tokens work without it). In the real file the indentation is tabs; match
the four lines regardless of leading whitespace:

```xml
		<dependency>
			<groupId>org.thymeleaf.extras</groupId>
			<artifactId>thymeleaf-extras-springsecurity6</artifactId>
		</dependency>
```

**Edit 2** — overwrite `src/test/java/com/example/todo/TestcontainersConfiguration.java`
with the content below. (The generated file differs only in using
`postgres:latest`; pinning the image is the point of this edit.) If the
generated file imports any container class other than
`org.testcontainers.postgresql.PostgreSQLContainer`, STOP and report — the
Testcontainers version is not the one this plan was verified against.

```java
package com.example.todo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

}
```

Do not edit `TestTodoApplication.java` or `TodoApplicationTests.java`; they are
used as generated.

**Verify**:
- `grep -c '<parent>' pom.xml && grep -A1 'spring-boot-starter-parent' pom.xml | grep -c '4.2.0'` → prints `1` then `1`
- `grep -c 'extras-springsecurity6' pom.xml` → `0`
- `grep -oE '<artifactId>[a-z0-9-]+</artifactId>' pom.xml | LC_ALL=C sort` → exactly these 20 lines, in this order (`LC_ALL=C` is required: other locales sort `-test` differently):
  ```
  <artifactId>flyway-database-postgresql</artifactId>
  <artifactId>postgresql</artifactId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <artifactId>spring-boot-starter-data-jpa-test</artifactId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
  <artifactId>spring-boot-starter-flyway-test</artifactId>
  <artifactId>spring-boot-starter-flyway</artifactId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <artifactId>spring-boot-starter-security-test</artifactId>
  <artifactId>spring-boot-starter-security</artifactId>
  <artifactId>spring-boot-starter-thymeleaf-test</artifactId>
  <artifactId>spring-boot-starter-thymeleaf</artifactId>
  <artifactId>spring-boot-starter-validation-test</artifactId>
  <artifactId>spring-boot-starter-validation</artifactId>
  <artifactId>spring-boot-starter-webmvc-test</artifactId>
  <artifactId>spring-boot-starter-webmvc</artifactId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <artifactId>testcontainers-junit-jupiter</artifactId>
  <artifactId>testcontainers-postgresql</artifactId>
  <artifactId>todo</artifactId>
  ```
- `./mvnw -B test | grep -E 'Tests run: [0-9]+, Fail.*Skipped: [0-9]+$|BUILD'` →
  `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`
  (a `Using generated security password` line also appears at this step — it disappears in Step 2)

Commit.

### Step 2: Add configuration, schema, entity and repository (with repository tests)

Replace `src/main/resources/application.properties` with exactly:

```properties
spring.application.name=todo
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/todo}
spring.datasource.username=${DB_USERNAME:todo}
spring.datasource.password=${DB_PASSWORD:}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
```

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` are read from the environment at run
time. Never commit a real password; the empty default is intentional. Tests do
not use these values — `@ServiceConnection` overrides the datasource with the
container's.

Create `src/main/resources/db/migration/V1__create_todo.sql`:

```sql
CREATE TABLE todo (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title      VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    completed  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

Create `src/main/java/com/example/todo/Todo.java`:

```java
package com.example.todo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "todo")
public class Todo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false)
	private boolean completed;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Todo() {
	}

	public Todo(String title) {
		this.title = title;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public String getTitle() { return title; }
	public boolean isCompleted() { return completed; }
	public Instant getCreatedAt() { return createdAt; }
	public void setTitle(String title) { this.title = title; }
	public void toggle() { this.completed = !this.completed; }
}
```

Create `src/main/java/com/example/todo/TodoRepository.java`:

```java
package com.example.todo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TodoRepository extends JpaRepository<Todo, Long> {

	List<Todo> findAllByOrderByCreatedAtAscIdAsc();

	List<Todo> findByCompletedOrderByCreatedAtAscIdAsc(boolean completed);

	long countByCompleted(boolean completed);

	@Modifying
	@Query("delete from Todo t where t.completed = true")
	int deleteCompleted();
}
```

Create `src/test/java/com/example/todo/TodoRepositoryTests.java`.
`@AutoConfigureTestDatabase(replace = NONE)` is required: without it
`@DataJpaTest` tries to swap in an embedded database, and there is none on the classpath.

```java
package com.example.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TodoRepositoryTests {

	@Autowired
	TodoRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void filtersByCompletedInCreationOrder() {
		Todo a = repository.save(new Todo("a"));
		Todo b = repository.save(new Todo("b"));
		Todo c = repository.save(new Todo("c"));
		b.toggle();
		repository.flush();

		assertThat(repository.findAllByOrderByCreatedAtAscIdAsc()).extracting(Todo::getTitle).containsExactly("a", "b", "c");
		assertThat(repository.findByCompletedOrderByCreatedAtAscIdAsc(false)).extracting(Todo::getTitle).containsExactly("a", "c");
		assertThat(repository.findByCompletedOrderByCreatedAtAscIdAsc(true)).extracting(Todo::getTitle).containsExactly("b");
		assertThat(repository.countByCompleted(false)).isEqualTo(2);
	}

	@Test
	void deleteCompletedRemovesOnlyCompleted() {
		repository.save(new Todo("keep"));
		repository.save(new Todo("drop")).toggle();
		repository.flush();

		assertThat(repository.deleteCompleted()).isEqualTo(1);
		assertThat(repository.findAll()).extracting(Todo::getTitle).containsExactly("keep");
	}

	@Test
	void databaseRejectsBlankTitle() {
		assertThatThrownBy(() -> jdbc.sql("insert into todo (title) values ('   ')").update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
```

**Verify**:
- `./mvnw -B test -Dtest=TodoRepositoryTests | grep -E 'Tests run: [0-9]+, Fail.*Skipped: [0-9]+$|BUILD'` → `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`
- `./mvnw -B test -Dtest=TodoApplicationTests | grep -E 'Tests run: [0-9]+, Fail.*Skipped: [0-9]+$|BUILD'` → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` (this proves Flyway ran `V1` and Hibernate `validate` accepted the entity against it)
- `./mvnw -B test -Dtest=TodoApplicationTests | grep -c 'generated security password'` → `0`

Commit.

### Step 3: Add service, controller, security config, templates and web tests

Create each file with exactly this content.

`src/main/java/com/example/todo/TodoFilter.java`:

```java
package com.example.todo;

public enum TodoFilter { ALL, ACTIVE, COMPLETED }
```

`src/main/java/com/example/todo/TodoNotFoundException.java`:

```java
package com.example.todo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TodoNotFoundException extends RuntimeException {
	public TodoNotFoundException(long id) {
		super("Todo " + id + " not found");
	}
}
```

`src/main/java/com/example/todo/TodoService.java` — all mutations go through
here, inside a transaction; `rename`/`toggle` rely on JPA dirty checking (no
explicit `save`), which only works because the class is `@Transactional`:

```java
package com.example.todo;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TodoService {

	private final TodoRepository repository;

	public TodoService(TodoRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<Todo> list(TodoFilter filter) {
		return switch (filter) {
			case ALL -> repository.findAllByOrderByCreatedAtAscIdAsc();
			case ACTIVE -> repository.findByCompletedOrderByCreatedAtAscIdAsc(false);
			case COMPLETED -> repository.findByCompletedOrderByCreatedAtAscIdAsc(true);
		};
	}

	@Transactional(readOnly = true)
	public long activeCount() {
		return repository.countByCompleted(false);
	}

	@Transactional(readOnly = true)
	public Todo get(long id) {
		return repository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
	}

	public Todo create(String title) {
		return repository.save(new Todo(title.strip()));
	}

	public void rename(long id, String title) {
		get(id).setTitle(title.strip());
	}

	public void toggle(long id) {
		get(id).toggle();
	}

	public void delete(long id) {
		repository.delete(get(id));
	}

	public int clearCompleted() {
		return repository.deleteCompleted();
	}
}
```

`src/main/java/com/example/todo/TodoForm.java`:

```java
package com.example.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TodoForm(@NotBlank @Size(max = 200) String title) {
}
```

`src/main/java/com/example/todo/TodoController.java`:

```java
package com.example.todo;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TodoController {

	private final TodoService service;

	public TodoController(TodoService service) {
		this.service = service;
	}

	@GetMapping("/")
	String root() {
		return "redirect:/todos";
	}

	@GetMapping("/todos")
	String list(@RequestParam(defaultValue = "ALL") TodoFilter filter, Model model) {
		populateList(filter, model);
		model.addAttribute("form", new TodoForm(""));
		return "todos/list";
	}

	@PostMapping("/todos")
	String create(@RequestParam(defaultValue = "ALL") TodoFilter filter,
			@Valid @ModelAttribute("form") TodoForm form, BindingResult result, Model model) {
		if (result.hasErrors()) {
			populateList(filter, model);
			return "todos/list";
		}
		service.create(form.title());
		return redirectTo(filter);
	}

	@PostMapping("/todos/{id}/toggle")
	String toggle(@PathVariable long id, @RequestParam(defaultValue = "ALL") TodoFilter filter) {
		service.toggle(id);
		return redirectTo(filter);
	}

	@PostMapping("/todos/{id}/delete")
	String delete(@PathVariable long id, @RequestParam(defaultValue = "ALL") TodoFilter filter) {
		service.delete(id);
		return redirectTo(filter);
	}

	@PostMapping("/todos/clear-completed")
	String clearCompleted(@RequestParam(defaultValue = "ALL") TodoFilter filter) {
		service.clearCompleted();
		return redirectTo(filter);
	}

	@GetMapping("/todos/{id}/edit")
	String edit(@PathVariable long id, Model model) {
		Todo todo = service.get(id);
		model.addAttribute("todo", todo);
		model.addAttribute("form", new TodoForm(todo.getTitle()));
		return "todos/edit";
	}

	@PostMapping("/todos/{id}")
	String rename(@PathVariable long id, @Valid @ModelAttribute("form") TodoForm form,
			BindingResult result, Model model) {
		if (result.hasErrors()) {
			model.addAttribute("todo", service.get(id));
			return "todos/edit";
		}
		service.rename(id, form.title());
		return "redirect:/todos";
	}

	private void populateList(TodoFilter filter, Model model) {
		model.addAttribute("todos", service.list(filter));
		model.addAttribute("activeCount", service.activeCount());
		model.addAttribute("filter", filter);
	}

	private static String redirectTo(TodoFilter filter) {
		return "redirect:/todos?filter=" + filter.name();
	}
}
```

`src/main/java/com/example/todo/SecurityConfig.java` — CSRF is on by default in
Spring Security; do **not** call `.csrf(...)` at all:

```java
package com.example.todo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		return http
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.build();
	}
}
```

`src/main/resources/templates/todos/list.html` — every `<form>` must use
`th:action="@{...}"` so the CSRF token is injected; every user-supplied value
is rendered with `th:text` (escaped), **never** `th:utext`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<head>
    <meta charset="UTF-8">
    <title>Todos</title>
</head>
<body>
<h1>Todos</h1>

<form th:action="@{/todos(filter=${filter})}" th:object="${form}" method="post">
    <input type="text" th:field="*{title}" placeholder="What needs to be done?" maxlength="200" autofocus>
    <button type="submit">Add</button>
    <p class="error" th:if="${#fields.hasErrors('title')}" th:errors="*{title}">Title error</p>
</form>

<ul id="todo-list">
    <li th:each="todo : ${todos}" th:classappend="${todo.completed} ? 'completed'">
        <form th:action="@{/todos/{id}/toggle(id=${todo.id},filter=${filter})}" method="post" style="display:inline">
            <button type="submit" th:text="${todo.completed} ? 'Undo' : 'Done'">Done</button>
        </form>
        <span class="title" th:text="${todo.title}">Title</span>
        <a th:href="@{/todos/{id}/edit(id=${todo.id})}">Edit</a>
        <form th:action="@{/todos/{id}/delete(id=${todo.id},filter=${filter})}" method="post" style="display:inline">
            <button type="submit">Delete</button>
        </form>
    </li>
</ul>

<p>
    <span id="active-count" th:text="${activeCount} + ' item(s) left'">0 item(s) left</span>
    |
    <a th:href="@{/todos(filter=ALL)}">All</a>
    <a th:href="@{/todos(filter=ACTIVE)}">Active</a>
    <a th:href="@{/todos(filter=COMPLETED)}">Completed</a>
</p>
<form th:action="@{/todos/clear-completed(filter=${filter})}" method="post">
    <button type="submit">Clear completed</button>
</form>
</body>
</html>
```

`src/main/resources/templates/todos/edit.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<head>
    <meta charset="UTF-8">
    <title>Edit todo</title>
</head>
<body>
<h1>Edit todo</h1>
<form th:action="@{/todos/{id}(id=${todo.id})}" th:object="${form}" method="post">
    <input type="text" th:field="*{title}" maxlength="200" autofocus>
    <button type="submit">Save</button>
    <a th:href="@{/todos}">Cancel</a>
    <p class="error" th:if="${#fields.hasErrors('title')}" th:errors="*{title}">Title error</p>
</form>
</body>
</html>
```

`src/main/resources/templates/error/404.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<head>
    <meta charset="UTF-8">
    <title>Not found</title>
</head>
<body>
<h1>Not found</h1>
<p>That todo does not exist.</p>
<a th:href="@{/todos}">Back to the list</a>
</body>
</html>
```

`src/test/java/com/example/todo/TodoWebTests.java` — full stack: MockMvc → controller → service →
real PostgreSQL. Every POST needs `.with(csrf())` except the one test that
proves a POST without it is rejected:

```java
package com.example.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TodoWebTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	TodoRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void rootRedirectsToList() throws Exception {
		mvc.perform(get("/")).andExpect(redirectedUrl("/todos"));
	}

	@Test
	void createThenListShowsTodo() throws Exception {
		mvc.perform(post("/todos").param("title", "  Buy milk  ").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/todos?filter=ALL"));

		assertThat(repository.findAll()).extracting(Todo::getTitle).containsExactly("Buy milk");
		mvc.perform(get("/todos"))
			.andExpect(status().isOk())
			.andExpect(view().name("todos/list"))
			.andExpect(content().string(containsString("Buy milk")))
			.andExpect(content().string(containsString("1 item(s) left")));
	}

	@Test
	void blankTitleIsRejectedAndNothingSaved() throws Exception {
		mvc.perform(post("/todos").param("title", "   ").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(view().name("todos/list"))
			.andExpect(model().attributeHasFieldErrors("form", "title"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void titleLongerThan200IsRejected() throws Exception {
		mvc.perform(post("/todos").param("title", "x".repeat(201)).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("form", "title"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void toggleMarksCompletedAndBack() throws Exception {
		Todo todo = repository.save(new Todo("t"));
		mvc.perform(post("/todos/{id}/toggle", todo.getId()).param("filter", "ACTIVE").with(csrf()))
			.andExpect(redirectedUrl("/todos?filter=ACTIVE"));
		assertThat(repository.findById(todo.getId()).orElseThrow().isCompleted()).isTrue();
		mvc.perform(post("/todos/{id}/toggle", todo.getId()).with(csrf()));
		assertThat(repository.findById(todo.getId()).orElseThrow().isCompleted()).isFalse();
	}

	@Test
	void filterShowsOnlyMatchingTodos() throws Exception {
		repository.save(new Todo("open-item"));
		Todo done = repository.save(new Todo("done-item"));
		mvc.perform(post("/todos/{id}/toggle", done.getId()).with(csrf()));

		mvc.perform(get("/todos").param("filter", "ACTIVE"))
			.andExpect(content().string(containsString("1 item(s) left")))
			.andExpect(content().string(containsString("open-item")))
			.andExpect(content().string(not(containsString("done-item"))));
		mvc.perform(get("/todos").param("filter", "COMPLETED"))
			.andExpect(content().string(containsString("done-item")))
			.andExpect(content().string(not(containsString("open-item"))));
	}

	@Test
	void deleteRemovesTodo() throws Exception {
		Todo keep = repository.save(new Todo("keep"));
		Todo todo = repository.save(new Todo("drop"));
		mvc.perform(post("/todos/{id}/delete", todo.getId()).param("filter", "COMPLETED").with(csrf()))
			.andExpect(redirectedUrl("/todos?filter=COMPLETED"));
		assertThat(repository.findAll()).extracting(Todo::getId).containsExactly(keep.getId());
	}

	@Test
	void clearCompletedKeepsActive() throws Exception {
		repository.save(new Todo("keep"));
		Todo done = repository.save(new Todo("drop"));
		mvc.perform(post("/todos/{id}/toggle", done.getId()).with(csrf()));

		mvc.perform(post("/todos/clear-completed").with(csrf())).andExpect(redirectedUrl("/todos?filter=ALL"));
		assertThat(repository.findAll()).extracting(Todo::getTitle).containsExactly("keep");
	}

	@Test
	void editRenamesTodo() throws Exception {
		Todo todo = repository.save(new Todo("old"));
		mvc.perform(get("/todos/{id}/edit", todo.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"old\"")));
		mvc.perform(post("/todos/{id}", todo.getId()).param("title", "  new  ").with(csrf()))
			.andExpect(redirectedUrl("/todos"));
		assertThat(repository.findById(todo.getId()).orElseThrow().getTitle()).isEqualTo("new");
	}

	@Test
	void editWithBlankTitleKeepsOldTitle() throws Exception {
		Todo todo = repository.save(new Todo("old"));
		mvc.perform(post("/todos/{id}", todo.getId()).param("title", "").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(view().name("todos/edit"))
			.andExpect(model().attributeHasFieldErrors("form", "title"));
		assertThat(repository.findById(todo.getId()).orElseThrow().getTitle()).isEqualTo("old");
	}

	@Test
	void unknownIdReturns404() throws Exception {
		mvc.perform(get("/todos/{id}/edit", 999999)).andExpect(status().isNotFound());
		mvc.perform(post("/todos/{id}/toggle", 999999).with(csrf())).andExpect(status().isNotFound());
		mvc.perform(post("/todos/{id}/delete", 999999).with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	void invalidFilterReturns400() throws Exception {
		mvc.perform(get("/todos").param("filter", "BOGUS")).andExpect(status().isBadRequest());
	}

	@Test
	void postWithoutCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/todos").param("title", "forged")).andExpect(status().isForbidden());
		assertThat(repository.count()).isZero();
	}

	@Test
	void everyFormCarriesCsrfToken() throws Exception {
		Todo todo = repository.save(new Todo("t"));
		assertEveryFormHasCsrfToken(mvc.perform(get("/todos")).andReturn().getResponse().getContentAsString(), 4);
		assertEveryFormHasCsrfToken(mvc.perform(get("/todos/{id}/edit", todo.getId())).andReturn().getResponse().getContentAsString(), 1);
	}

	private static void assertEveryFormHasCsrfToken(String html, int expectedForms) {
		assertThat(html.split("<form", -1).length - 1).isEqualTo(expectedForms);
		assertThat(html.split("name=\"_csrf\"", -1).length - 1).isEqualTo(expectedForms);
	}

	@Test
	void titleIsHtmlEscaped() throws Exception {
		repository.save(new Todo("<script>alert(1)</script>"));
		mvc.perform(get("/todos"))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
	}
}
```

**Verify**:
- `./mvnw -B test -Dtest=TodoWebTests | grep -E 'Tests run: [0-9]+, Fail.*Skipped: [0-9]+$|BUILD'` → `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`
- `grep -rn 'th:utext' src/main/resources/templates` → no output
- `grep -c '<form' src/main/resources/templates/todos/list.html` and `grep -c '<form th:action=' src/main/resources/templates/todos/list.html` → both print `4`
- `grep -rn 'csrf' src/main/java` → no output (CSRF must not be configured, i.e. not disabled)

Commit.

### Step 4: Add a README

Create `README.md` with these sections (prose is yours, the commands must be exactly these):

- **Requirements**: Java 25, Docker (for tests and for the dev run).
- **Run tests**: `./mvnw verify`
- **Run locally with a throwaway database**: `./mvnw spring-boot:test-run`, then open http://localhost:8080 (data is lost when the app stops).
- **Run against your own PostgreSQL**: set `DB_URL` (default `jdbc:postgresql://localhost:5432/todo`), `DB_USERNAME` (default `todo`), `DB_PASSWORD`, then `./mvnw spring-boot:run`. Flyway creates the schema on startup.
- **Security note**: no login; CSRF protection is on; do not expose the app beyond a trusted network without adding authentication.

**Verify**: `grep -c -e './mvnw verify' -e './mvnw spring-boot:test-run' -e 'DB_URL' README.md` → `3` or more.

Commit.

### Step 5: Full verification, dev run, falsifiability bar

1. `./mvnw -B verify | grep -E 'Tests run: [0-9]+, Fail.*Skipped: [0-9]+$|BUILD'` → `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
2. Dev-run smoke test. Run this script from the repository root as one `bash`
   invocation (the log and cookie jar go to temp files **outside** the repo, so
   nothing is left behind). It was run on the scratch build; expected output
   is shown after it:

   ```bash
   LOG=$(mktemp) JAR=$(mktemp)
   ./mvnw -q spring-boot:test-run > "$LOG" 2>&1 &
   PID=$!
   for i in $(seq 1 180); do grep -q 'Tomcat started on port 8080' "$LOG" && break; sleep 1; done
   grep -c 'Tomcat started on port 8080' "$LOG"
   curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' localhost:8080/
   TOKEN=$(curl -s -c "$JAR" localhost:8080/todos | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="\([^"]*\)"/\1/')
   curl -s -b "$JAR" -o /dev/null -w '%{http_code} %{redirect_url}\n' -d "_csrf=$TOKEN" -d 'title=smoke-test' 'localhost:8080/todos?filter=ALL'
   curl -s -b "$JAR" localhost:8080/todos | grep -c 'smoke-test'
   curl -s -b "$JAR" -o /dev/null -w '%{http_code}\n' -d 'title=forged' 'localhost:8080/todos'
   curl -s -H 'Accept: text/html' localhost:8080/todos/999999/edit | grep -c 'That todo does not exist'
   kill $PID; wait $PID 2>/dev/null; sleep 2
   curl -s -o /dev/null localhost:8080 || echo stopped
   rm -f "$LOG" "$JAR"
   ```

   Expected output, line by line: `1`, `302 http://localhost:8080/todos`,
   `302 http://localhost:8080/todos?filter=ALL`, `1`, `403`, `1`, `stopped`.
   (First run may take minutes while Docker pulls the image; the loop waits up
   to 180 s. If the first line is `0`, read the log before it is deleted —
   remove the `rm` line and rerun.) Do **not** stop the app with
   `pkill -f spring-boot:test-run`: that pattern can match and kill your own shell.
   The `Accept: text/html` header matters: without it Boot answers 404s with JSON instead of `error/404.html`.
3. Run every row of the falsifiability bar below.

## Test plan & coverage

All tests run against real PostgreSQL 18 in a container — **no H2, no mocks of
the repository or service**. Two layers:

- **Repository layer** (`TodoRepositoryTests`, `@DataJpaTest`, rolls back after each test):
  - `filtersByCompletedInCreationOrder` — ALL/ACTIVE/COMPLETED queries return the right rows in creation order; active count.
  - `deleteCompletedRemovesOnlyCompleted` — bulk delete touches only completed rows.
  - `databaseRejectsBlankTitle` — the DB `CHECK` constraint rejects a whitespace-only title even when the application layer is bypassed.
- **Application context** (`TodoApplicationTests#contextLoads`, generated) — Flyway migration applies and Hibernate `validate` accepts the mapping.
- **Web layer, full stack** (`TodoWebTests`, `@SpringBootTest` + MockMvc + container; `@BeforeEach` empties the table because these tests do not roll back):
  - `rootRedirectsToList`
  - `createThenListShowsTodo` — title trimmed, PRG redirect keeps the filter, list renders it, active counter.
  - `blankTitleIsRejectedAndNothingSaved` — HTTP 200, field error, zero rows.
  - `titleLongerThan200IsRejected` — HTTP 200, field error, zero rows.
  - `toggleMarksCompletedAndBack` — flips both ways; redirect preserves `filter=ACTIVE`.
  - `filterShowsOnlyMatchingTodos` — ACTIVE and COMPLETED views exclude the other kind; the active counter reads `1 item(s) left` with one open and one completed todo.
  - `deleteRemovesTodo` — with two todos, only the targeted one is removed; redirect preserves `filter=COMPLETED`.
  - `clearCompletedKeepsActive`
  - `editRenamesTodo` — edit form is pre-filled; rename persists, trimmed (`"  new  "` → `new`).
  - `editWithBlankTitleKeepsOldTitle`
  - `unknownIdReturns404` — edit, toggle and delete on a missing id.
  - `invalidFilterReturns400`
  - `postWithoutCsrfTokenIsRejected` — HTTP 403 and nothing saved.
  - `everyFormCarriesCsrfToken` — number of `_csrf` inputs equals number of `<form>` tags on the list page (4 with one todo) and the edit page (1).
  - `titleIsHtmlEscaped` — `<script>` in a title is rendered escaped.

Coverage bar: every controller endpoint, every `TodoService` method (including the trim in both `create` and `rename`), both outcomes of each validation
branch (valid → redirect, invalid → re-render), the not-found branch of
`TodoService.get`, each `TodoFilter` arm, and both CSRF properties (requests
without a token are refused; every rendered form has one).

Deliberately not covered:
- Concurrency (two tabs editing the same todo): last write wins, acceptable for a single-user app.
- Visual layout / browser behaviour (no Selenium/Playwright): templates are asserted through rendered HTML only.
- `TestTodoApplication` / `spring-boot:test-run` and the rendered `error/404.html` page — checked by the Step 5.2 smoke script, not by a test (MockMvc does not perform the error-page dispatch).
- Running against a non-container PostgreSQL (`DB_URL` path) — configuration only; Flyway behaviour is the same.

**Verification**: `./mvnw -B verify` → `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`.

### Falsifiability bar

Commit all work first. For each row: apply the mutation, run the test command,
copy the failure lines (`grep -E '^\[ERROR\]   '`) into your report, then
restore with `git checkout -- <file>` and confirm `git diff --exit-code` is
clean. All rows were run on the scratch build on 2026-11-23; the "Expected
failure" column is what was observed there.

| # | Mutation (file: change) | Test command | Expected failure |
|---|---|---|---|
| 1 | `templates/todos/list.html`: `th:text="${todo.title}"` → `th:utext="${todo.title}"` | `-Dtest='TodoWebTests#titleIsHtmlEscaped'` | assertion failure `Response content` |
| 2 | `TodoController.java`, `create` method: remove `@Valid` before `@ModelAttribute("form")` | `-Dtest='TodoWebTests#blankTitleIsRejectedAndNothingSaved'` | error: `DataIntegrityViolationException ... violates check constraint "todo_title_check"` (the request reached the DB instead of being rejected by validation) |
| 3 | `TodoForm.java`: delete ` @Size(max = 200)` | `-Dtest='TodoWebTests#titleLongerThan200IsRejected'` | error: `DataIntegrityViolationException ... value too long for type character varying(200)` |
| 4 | `TodoService.java`: `new Todo(title.strip())` → `new Todo(title)` | `-Dtest='TodoWebTests#createThenListShowsTodo'` | assertion failure at the `containsExactly("Buy milk")` line |
| 5 | `Todo.java`: `this.completed = !this.completed;` → `this.completed = true;` | `-Dtest='TodoWebTests#toggleMarksCompletedAndBack'` | assertion failure (expected `false` after the second toggle) |
| 6 | `TodoService.java`: `case ACTIVE -> repository.findByCompletedOrderByCreatedAtAscIdAsc(false);` → `case ACTIVE -> repository.findAllByOrderByCreatedAtAscIdAsc();` | `-Dtest='TodoWebTests#filterShowsOnlyMatchingTodos'` | assertion failure `Response content` |
| 7 | `TodoRepository.java`: `delete from Todo t where t.completed = true` → `delete from Todo t` | `-Dtest='TodoWebTests#clearCompletedKeepsActive'`, then `-Dtest='TodoRepositoryTests#deleteCompletedRemovesOnlyCompleted'` | assertion failure in each |
| 8 | `TodoNotFoundException.java`: delete the `@ResponseStatus(HttpStatus.NOT_FOUND)` line (leave the now-unused imports — it still compiles) | `-Dtest='TodoWebTests#unknownIdReturns404'` | error: `Request processing failed: com.example.todo.TodoNotFoundException: Todo 999999 not found` |
| 9 | `V1__create_todo.sql`: delete ` CHECK (length(trim(title)) > 0)` | `-Dtest='TodoRepositoryTests#databaseRejectsBlankTitle'` | assertion failure (no exception thrown) |
| 10 | `TodoController.java`, `redirectTo`: `return "redirect:/todos?filter=" + filter.name();` → `return "redirect:/todos";` | `-Dtest='TodoWebTests#toggleMarksCompletedAndBack'` | `Redirected URL expected:</todos?filter=ACTIVE> but was:</todos>` |
| 11 | `SecurityConfig.java`: append `.csrf(csrf -> csrf.disable())` after `.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())` | `-Dtest='TodoWebTests#postWithoutCsrfTokenIsRejected'` | `Status expected:<403> but was:<302>` |
| 12 | `templates/todos/list.html`: `<form th:action="@{/todos/clear-completed(filter=${filter})}" method="post">` → `<form action="/todos/clear-completed" method="post">` | `-Dtest='TodoWebTests#everyFormCarriesCsrfToken'` | assertion failure in `assertEveryFormHasCsrfToken` |
| 13 | `TodoService.java`: `return repository.countByCompleted(false);` → `return repository.count();` | `-Dtest='TodoWebTests#filterShowsOnlyMatchingTodos'` | assertion failure `Response content` |
| 14 | `TodoService.java`: `get(id).setTitle(title.strip());` → `get(id).setTitle(title);` | `-Dtest='TodoWebTests#editRenamesTodo'` | assertion failure (title is `"  new  "`) |
| 15 | `TodoService.java`: `repository.delete(get(id));` → `get(id); repository.deleteAll();` | `-Dtest='TodoWebTests#deleteRemovesTodo'` | assertion failure at `containsExactly(keep.getId())` |
| 16 | `TodoService.java`: delete the class-level `@Transactional` line (the one directly above `public class TodoService`, at column 0; leave the indented method-level ones) | `-Dtest='TodoWebTests#toggleMarksCompletedAndBack'`, then `-Dtest='TodoWebTests#editRenamesTodo'` | assertion failure in each (changes to the detached entity are never flushed) |

Prefix every test command with `./mvnw -B test` and pipe through `grep -E '^\[ERROR\]   |Tests run:'`.
If a row does **not** fail, that is a finding — report it; do not adjust the test to make it fail.

## Code review

**Executor: self-review before you report.** State the outcome of each in your report:

- Re-read the full diff against "Why this matters": all seven user actions (add, rename, toggle, delete, filter, clear completed, view) have a passing test, and the Step 5.2 smoke script produced the expected output. If you have a browser, also click through them once; if not, say so in the report — do not claim a click-through you did not do.
- Every file traces to a step. Nothing outside the in-scope list (`git status --short` shows only in-scope paths).
- Files match this plan's content exactly — if you changed anything (an import, a version), say what and why.
- No `th:utext`, no `.csrf(` call, no `@DynamicPropertySource`, no H2 dependency, no `*IT.java` test names.
- No real credentials anywhere (`grep -rn -i password src README.md` shows only the `${DB_PASSWORD:}` placeholder and README prose).
- Report honestly any verification you skipped or that failed.

**Reviewer: what to scrutinize in this specific change.**

- **Rows 2 and 3 of the falsifiability bar fail with a database exception, not a validation assertion.** That is expected (the DB constraint is the backstop), but confirm the passing tests assert `attributeHasFieldErrors` — i.e. that validation, not the DB, is what rejects the input in the happy path.
- **`rename` and `toggle` have no explicit `save`.** They depend on `TodoService` being `@Transactional` so dirty checking flushes (bar row 16 proves the tests catch its removal). Check the executor did not add `repository.save(...)` calls "for safety", which would hide that dependency and make row 16 stop failing.
- **CSRF**: confirm `SecurityConfig` contains no `.csrf(` call and that rows 11 and 12 were actually observed failing. A green suite does not prove CSRF is on — row 11 does.
- **Boot 4 package names**: a weaker executor may "fix" a compile error by importing a Boot 3 package. Check that all imports match the ones listed in "Current state".
- What a green suite would still not catch: a template that renders but is unusable in a real browser (only a manual click-through checks that; the smoke script checks status codes and text), and behaviour against a long-lived production database (only a fresh Flyway `V1` is ever tested).

## Done criteria

ALL must hold:

- [ ] `ls -A` shows only `.git .gitattributes .gitignore .mvn README.md mvnw mvnw.cmd plans pom.xml src` (plus `target` after a build, and `.claude` if it was there before)
- [ ] `./mvnw -B verify` → `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
- [ ] `./mvnw -B test -Dtest=TodoApplicationTests | grep -c 'generated security password'` → `0`
- [ ] `grep -rn 'th:utext' src/main/resources/templates` → no output
- [ ] `grep -rn 'csrf' src/main/java` → no output
- [ ] `grep -rln 'org.testcontainers.postgresql.PostgreSQLContainer' src/test` → exactly `src/test/java/com/example/todo/TestcontainersConfiguration.java`
- [ ] `grep -c 'postgres:18-alpine' src/test/java/com/example/todo/TestcontainersConfiguration.java` → `1`
- [ ] Step 0 preconditions all held
- [ ] Step 5.2 smoke script printed exactly `1`, `302 http://localhost:8080/todos`, `302 http://localhost:8080/todos?filter=ALL`, `1`, `403`, `1`, `stopped`
- [ ] **Every row of the falsifiability bar made to fail, with its observed failure output in the report.** A row that cannot be made to fail FAILS this criterion — report it as a finding
- [ ] `git status --short` clean after the bar (all mutations reverted)
- [ ] Code-review self-check completed; outcome in the report
- [ ] Last: `plans/README.md` status row updated and committed on its own (`Update plan 001 status`) — not applicable if a reviewer dispatched you and told you they maintain the index

## STOP conditions

Stop and report back (do not improvise) if:

- The drift check finds anything besides `.git`, `plans` (and `.claude`) in the repository root.
- Any Step 0 precondition fails (wrong JDK, no Docker, no git repo or identity, port 8080 busy before Step 5.2).
- The Step 1 `curl` exits non-zero, or — after Edit 1 — the `pom.xml` artifact list differs from the 20 lines in Step 1 — do **not** hand-write a pom or switch Boot version.
- An import listed in "Current state" does not resolve. Do not substitute a Boot 3 package name or a different Testcontainers class.
- Docker is not available (`docker info` fails) or Testcontainers cannot start a container — do **not** switch tests to H2 or mock the repository.
- `./mvnw` cannot download Maven or dependencies (no network).
- A step's verification fails twice after a reasonable fix attempt.
- You feel the need to add a dependency, plugin or file not listed in Scope.

## Maintenance notes

- **Schema changes** go in new migrations (`V2__...sql`, ...). Never edit `V1__create_todo.sql` after it has run against a real database — Flyway's checksum validation will refuse to start the app.
- **Title length** is defined in three places that must change together: `TodoForm` `@Size(max = 200)`, `Todo` `@Column(length = 200)`, and the migration's `VARCHAR(200)` (plus `maxlength="200"` in both templates). `ddl-auto=validate` does not check column length, so a mismatch is only caught by `titleLongerThan200IsRejected`.
- **Adding authentication later**: `SecurityConfig` is the single place; replace `permitAll()` with real rules, remove the `spring.autoconfigure.exclude` line from `application.properties`, and add a `todo.owner` column via a new migration. CSRF is already wired.
- **Adding JavaScript/fetch-based actions later**: those requests must send the CSRF token header, or they will get 403.
- **Postgres image**: bump `postgres:18-alpine` deliberately (and match production's major version); do not use `latest`, which changes under you.
- `TodoWebTests` does not roll back; it relies on `@BeforeEach repository.deleteAll()`. A new web test class sharing the same Spring context must do the same.
