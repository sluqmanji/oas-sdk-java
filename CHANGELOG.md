# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Randomized sequence tests (RST) aligned with Integrated-style flows: OAS-driven scenario templates (create/edit/delete + get), query-parameter positives, optional 401 expectations, `Location` header and `{param}` path resolution, scenario-biased sequences, and `GenerateRstFromPublishedSpec` (`mvn exec:java` with env `RST_*`) for published bundles without full SDK validation.
- Comprehensive logging system with file rotation
- Configurable logging via properties file, system properties, or environment variables
- Error level logging for all exception cases

### Changed
- Reorganized generated code structure to use `generated-code/` top-level folder
- Updated all integration tests to use new directory structure
- Improved reference resolution in OASParser

### Fixed
- Fixed encoding issues by using `StandardCharsets.UTF_8` consistently
- Fixed internal representation exposure with defensive copying
- Fixed locale-sensitive operations by using `Locale.ROOT`
- Fixed unchecked cast warnings using Util methods
- Fixed null pointer access issues
- Fixed raw type warnings in pattern matching
- Fixed YAML parsing errors in test resources
- Fixed System.err.println calls by replacing with proper logging
- Migrated File API to java.nio.file.Files for better robustness

## [2.0] - 2025-12-26

### Added
- Built-in Observability Framework (OpenTelemetry + Micrometer) for all generated languages
- JerseyGenerator decomposition (7,318 LOC -> 13 focused classes)
- Enhanced mock data generation with `$ref` resolution in allOf/oneOf/anyOf compositions
- OAS-driven randomized sequential testing with dependency graph inference
- Multi-language test generation (Java/JUnit5, Python/pytest, Node.js/Jest)
- Complete feature parity across Java, Python, and Node.js generators
- SLA monitoring with atomic sliding-window rate limiter and 7-panel Grafana dashboard
- Negative test case generation (empty body, malformed JSON, boundary values)
- RBAC, CORS, and rate limiting test generation
- Correlation ID propagation via CorrelationIdFilter

### Changed
- Simplified test resources (reduced from ~52,000 to ~1,100 lines)
- All file operations now use UTF-8 encoding explicitly
- String case conversions use Locale.ROOT for locale safety
- Resolved all high-priority SpotBugs warnings

### Fixed
- Encoding issues with StandardCharsets.UTF_8
- Defensive copying for mutable collections
- StackOverflowError blocks replaced with proper cycle detection
- Missing Base64 import in MockDataGenerator
- Dockerfile updated to eclipse-temurin:21-jre

## [1.17.0] - 2025-12-26

### Added
- Java Jersey/JAX-RS generator (100% feature complete)
- Python FastAPI generator (100% feature complete)
- Python Flask generator (100% feature complete)
- Node.js Express generator (100% feature complete)
- Comprehensive test generation (Java, Python, Node.js)
- Mock data generation
- SLA enforcement generation
- Professional documentation generation (Redocly, Swagger UI, Markdown)
- CLI interface for easy usage
- Support for OpenAPI 3.0 and 3.1 specifications
- Query parameter validation
- Security scheme extraction and annotation
- Inline schema model generation
- API version extraction from server URLs

### Changed
- Improved code quality with SpotBugs analysis
- Enhanced error handling and validation
- Optimized reference resolution

### Fixed
- Various bug fixes and improvements

## [1.0.0] - Initial Release

### Added
- Initial release of OAS SDK
- Basic code generation capabilities
- OpenAPI specification parsing
- Core SDK functionality

---

[Unreleased]: https://github.com/egain/oas-sdk-java/compare/v2.0...HEAD
[2.0]: https://github.com/egain/oas-sdk-java/compare/v1.17.0...v2.0
[1.17.0]: https://github.com/egain/oas-sdk-java/releases/tag/v1.17.0
[1.0.0]: https://github.com/egain/oas-sdk-java/releases/tag/v1.0.0

