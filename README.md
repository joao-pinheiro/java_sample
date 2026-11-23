# Todo

A single-user todo list web app: Spring Boot 4.2, PostgreSQL, Thymeleaf. Tests run against real PostgreSQL via Testcontainers.

## Requirements

Java 25 and Docker (for tests and for the dev run).

## Run tests

```
./mvnw verify
```

## Run locally with a throwaway database

```
./mvnw spring-boot:test-run
```

Then open http://localhost:8080. Data is lost when the app stops.

## Run against your own PostgreSQL

Set `DB_URL` (default `jdbc:postgresql://localhost:5432/todo`), `DB_USERNAME` (default `todo`) and `DB_PASSWORD`, then run `./mvnw spring-boot:run`. Flyway creates the schema on startup.

Requirements: the database and the `DB_USERNAME` role must already exist (Flyway does not create them). Use a dedicated, **empty** database owned by `DB_USERNAME`: PostgreSQL 15+ no longer lets non-owners create tables in `public`, and Flyway refuses a non-empty schema that has no Flyway history table. Do not "fix" that with `spring.flyway.baseline-on-migrate=true`: with the default baseline version `1` it skips `V1` and startup then fails schema validation.

## Package

`./mvnw package` runs the tests, so Docker is needed; add `-DskipTests` to skip them. Then run `java -jar target/todo-0.0.1-SNAPSHOT.jar` with the same `DB_*` variables. Requires a Java 25 runtime.

## Network and security

This is a **local, single-user app with no login**. It listens on `127.0.0.1` only (open http://127.0.0.1:8080 if `localhost` resolves only to `::1` on your machine) and answers only requests whose `Host` is `localhost` or `127.0.0.1`. CSRF protection is on.

The Host allowlist protects your browser against DNS rebinding; it is **not access control**: any program that can connect can send `Host: localhost`. Running the app anywhere else (another machine, on `0.0.0.0`, behind a reverse proxy) is **unsupported**: anyone who can reach it can read and change the list. That needs authentication first, which this app does not have.

Do not add, change or remove any `server.tomcat.remoteip.*` or `server.request.header.forwarded.*` property. The two already in `application.properties` (`server.request.header.forwarded.strategy=none` and the deliberately empty `server.tomcat.remoteip.host-header=`) are required security settings, and any other value lets `X-Forwarded-Host` bypass the allowlist.

`SERVER_ADDRESS` (default `127.0.0.1`) exists for development only.

`ALLOWED_HOSTS` (comma-separated hostnames without port; an empty value rejects every request) exists for development only.

## Testcontainers

Needs a Docker-compatible daemon found via `DOCKER_HOST` or the default socket; Docker CLI contexts are not read. For Podman, point `DOCKER_HOST` at the Podman socket. Docker publishes container ports on all network interfaces, including the PostgreSQL and Testcontainers' Ryuk containers during `spring-boot:test-run`; on an untrusted network set `"ip": "127.0.0.1"` in Docker's `daemon.json`.
