# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OAS SDK Java is an OpenAPI-first development framework that generates production-ready applications, test suites, documentation, and SLA enforcement from OpenAPI specifications. It supports multiple languages (Java/Jersey, Python/FastAPI/Flask, Node.js/Express) with built-in observability (OpenTelemetry + Micrometer).

## Build & Test Commands

```bash
# Build
mvn clean compile
mvn clean install              # full build with tests

# Run all tests (unit tests run in parallel with 4 threads)
mvn test

# Run a single test class
mvn test -Dtest=JerseyGeneratorTest

# Run a single test method
mvn test -Dtest=JerseyGeneratorTest#testMethodName

# Integration tests only (matches *IT.java, *IntegrationTest.java)
mvn verify

# Code coverage report (outputs to target/site/jacoco/)
mvn verify -Pjacoco

# Static analysis with SpotBugs
mvn spotbugs:check

# Build fat JAR (shaded, main class: egain.oassdk.cli.OASSDKCLI)
mvn package
```

## Architecture

**Single-module Maven project** (Java 21, packaging: jar) with these key packages under `egain.oassdk`:

- **`cli`** — PicoCLI-based CLI entry point (`OASSDKCLI`). Commands: `generate`, `tests`, `validate`, `docs`, `all`.
- **`config`** — Configuration classes with Builder pattern: `GeneratorConfig`, `TestConfig`, `SLAConfig`, `ObservabilityConfig`.
- **`core`** — OpenAPI parsing (`OASParser`), validation (`OASValidator`), metadata extraction (`OASMetadata`). Parser handles external `$ref` resolution and ZIP-based spec loading.
- **`generators`** — Code generation via Factory + Strategy pattern:
  - `GeneratorFactory` creates language/framework-specific generators (`CodeGenerator` interface)
  - `generators.java` — Java/Jersey generator decomposed into focused classes: `JerseyGenerator` (orchestrator), `JerseyModelGenerator` (POJOs from schemas), `JerseyResourceGenerator` (JAX-RS resources), `JerseySchemaOneOfXor` (simple oneOf XOR for models), `JerseyBuildGenerator` (pom.xml), `JerseyObservabilityGenerator`, `JerseyAuthorizationDataGenerator`, etc.
  - `generators.python` — FastAPI and Flask generators
  - `generators.nodejs` — Express generator
  - `generators.go`, `generators.csharp` — Stubs
- **`testgenerators`** — Test generation via `TestGeneratorFactory`. Subtypes: `unit`, `integration`, `nfr`, `performance`, `security`, `postman`, `mock`.
- **`docs`** — Documentation generation (Swagger UI, Markdown, FreeMarker templates).
- **`sla`** — SLA enforcement: rate limiting, API gateway policies, Prometheus/Grafana monitoring.

**Main SDK entry point**: `OASSDK.java` (implements `AutoCloseable` for ZIP FileSystem cleanup). Orchestrates parsing → validation → generation pipeline.

## Key Patterns & Conventions

- **Factory pattern**: `GeneratorFactory` and `TestGeneratorFactory` use switch expressions to create generators by language/framework.
- **Builder pattern**: `GeneratorConfig.builder()` for fluent configuration.
- **Java 21 features used throughout**: pattern matching with `instanceof`, switch expressions, text blocks, records. Use these when writing new code.
- **FreeMarker templates** in `src/main/resources/templates/` for documentation generation.
- **All file I/O** uses `java.nio.file.Files` with explicit UTF-8 encoding.
- **String operations** use `Locale.ROOT` for locale safety.
- **Defensive copying** for mutable collections in public APIs.

## Test Resources

OpenAPI test specs live in `src/test/resources/` (openapi1-5.yaml, sla.yaml, etc.). Generated sample projects are in `generated-code/`.

## Dependencies

Key libraries: Jackson 2.15.2 (YAML/JSON), Swagger Parser 2.1.16 (OpenAPI parsing), Jersey 3.1.3 (JAX-RS), PicoCLI 4.7.5 (CLI), FreeMarker 2.3.32 (templates), JUnit 5.10.0 + Mockito 5.5.0 + AssertJ 3.24.2 (testing), JavaFaker 1.0.2 (mock data).

## Code Quality

- **SpotBugs**: Max effort, Low threshold
- **JaCoCo**: 50% line coverage minimum (60% line + 50% branch for release profile)
- **Commit messages**: Start with a verb (Add, Fix, Update, Remove), reference issue numbers
