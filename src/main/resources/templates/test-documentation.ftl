# Test Documentation

This document describes the testing strategy and implementation for the ${apiTitle} API.

## Test Types

| Test Type | Purpose | Location | Framework | Coverage |
|-----------|---------|----------|-----------|----------|
<#list testTypes as testType>
<#if testType.enabled>
| ${testType.name} | ${testType.purpose} | \`${testType.location}\` | ${testType.framework} | ${testType.coverage} |
</#if>
</#list>

## Running Tests

```bash
<#list testCommands as command>
${command}
</#list>
```

## Test Data

Test data is generated automatically based on the OpenAPI specification.
Mock data files are located in \`${mockDataLocation}\`.

## Postman Collection

A Postman collection is generated for manual testing:
- Collection file: \`${postmanCollection}\`
- Environment file: \`${postmanEnvironment}\`
- Run with Newman: \`newman run ${postmanCollection} -e ${postmanEnvironment}\`

## Schemathesis (contract testing)

When the \`schemathesis\` test type is generated via the OAS SDK, the output bundle typically includes:

- \`openapi.yaml\` — resolved OpenAPI document used as the schema source
- \`schemathesis.properties\` — URLs, report paths, and options; CI may replace tokens such as \`%BASEURL%\`, \`%TOKEN%\`, \`%HUB%\`, \`%DOT%\`, \`%BUILD_NUMBER%\`
- \`run-schemathesis.sh\` — invokes the \`st\` CLI (\`pip install schemathesis\`)
- \`README-schemathesis.md\` — short runbook inside the bundle

From the project CLI: \`oas-sdk tests <spec.yaml> -t schemathesis -o <outputDir> [--url <baseUrl>] [--run]\`.

## Test Configuration

<#if testConfig.unitTestsEnabled>
### Unit Tests
- **Enabled**: Yes
- **Framework**: JUnit 5
- **Location**: \`src/test/java/\`
- **Coverage**: Controllers, Services, Models

</#if>
<#if testConfig.integrationTestsEnabled>
### Integration Tests
- **Enabled**: Yes
- **Framework**: Jersey Test
- **Location**: \`src/test/java/integration/\`
- **Coverage**: API endpoints, Database interactions

</#if>
<#if testConfig.nfrTestsEnabled>
### NFR Tests
- **Enabled**: Yes
- **Framework**: Custom + JUnit 5
- **Location**: \`src/test/java/nfr/\`
- **Coverage**: Performance, Scalability, Reliability

</#if>
<#if testConfig.performanceTestsEnabled>
### Performance Tests
- **Enabled**: Yes
- **Framework**: JMeter, Gatling
- **Location**: \`src/test/java/performance/\`
- **Coverage**: Response time, Throughput, Resource usage

</#if>
<#if testConfig.securityTestsEnabled>
### Security Tests
- **Enabled**: Yes
- **Framework**: OWASP ZAP, Custom
- **Location**: \`src/test/java/security/\`
- **Coverage**: Authentication, Authorization, Input validation

</#if>

## Test Execution

### Maven Commands

```bash
# Run all tests
mvn test

# Run specific test types
mvn test -Dtest=*UnitTest
mvn test -Dtest=*IntegrationTest
mvn test -Dtest=*NFRTest
mvn test -Dtest=*PerformanceTest
mvn test -Dtest=*SecurityTest

# Run with coverage
mvn test jacoco:report

# Run integration tests
mvn verify
```

### Docker Commands

```bash
# Run tests in Docker
docker run --rm -v $(pwd):/app -w /app maven:3.8-openjdk-21 mvn test

# Run specific test type
docker run --rm -v $(pwd):/app -w /app maven:3.8-openjdk-21 mvn test -Dtest=*UnitTest
```

## Test Reports

After running tests, the following reports are generated:

- **Surefire Reports**: \`target/surefire-reports/\`
- **Jacoco Coverage**: \`target/site/jacoco/\`
- **Test Results**: \`target/test-results/\`

## Continuous Integration

The project includes GitHub Actions workflows for:

- **Unit Tests**: Run on every push and pull request
- **Integration Tests**: Run on pull requests and releases
- **Performance Tests**: Run on releases
- **Security Tests**: Run on pull requests

## Troubleshooting

### Common Issues

1. **Test Failures**: Check the surefire reports for detailed error messages
2. **Coverage Issues**: Ensure all code paths are covered by tests
3. **Integration Test Failures**: Verify database and external service connections
4. **Performance Test Failures**: Check resource limits and test data

### Debug Commands

```bash
# Run tests with debug output
mvn test -X

# Run specific test with debug
mvn test -Dtest=SpecificTest -X

# Run tests with specific profile
mvn test -Ptest-profile
```

## Best Practices

1. **Test Naming**: Use descriptive test method names
2. **Test Organization**: Group related tests in the same class
3. **Test Data**: Use realistic test data that matches production
4. **Test Isolation**: Ensure tests don't depend on each other
5. **Test Coverage**: Aim for high code coverage but focus on critical paths
6. **Test Performance**: Keep unit tests fast, integration tests reasonable
7. **Test Maintenance**: Update tests when requirements change

## Support

For issues with testing:

- Check the test documentation
- Review test reports and logs
- Consult the development team
- Open an issue in the project repository
