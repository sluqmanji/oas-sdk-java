package egain.oassdk.test.schemathesis;

import egain.oassdk.core.exceptions.GenerationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Executes Schemathesis tests from OpenAPI specification
 */
public class SchemathesisTestRunner {

    /**
     * Execute Schemathesis tests
     *
     * @param spec      OpenAPI specification
     * @param outputDir Output directory for test results
     * @param baseUrl   Base URL for the API under test
     * @throws GenerationException if test execution fails
     */
    public void executeTests(Map<String, Object> spec, String outputDir, String baseUrl) throws GenerationException {
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Generate Schemathesis configuration
            generateSchemathesisConfig(spec, outputDir, baseUrl);

            // Generate test execution script
            generateTestExecutionScript(spec, outputDir, baseUrl);

            // Generate Docker configuration for Schemathesis
            generateDockerConfig(spec, outputDir, baseUrl);

            // Generate CI/CD integration
            generateCICDIntegration(spec, outputDir, baseUrl);

        } catch (Exception e) {
            throw new GenerationException("Failed to execute Schemathesis tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate Schemathesis configuration
     */
    private void generateSchemathesisConfig(Map<String, Object> spec, String outputDir, String baseUrl) throws IOException {
        String config = """
                # Schemathesis configuration
                base_url: %s
                schema: openapi.yaml
                
                # Test configuration
                workers: 4
                max_failures: 10
                max_response_time: 5000
                max_requests: 1000
                
                # Test selection
                test_case_id: "test_{method}_{path}"
                
                # Test data generation
                data_generation:
                  method: "hypothesis"
                  seed: 42
                
                # Test execution
                execution:
                  timeout: 30
                  retry_count: 3
                  retry_delay: 1
                
                # Reporting
                reporting:
                  format: "json"
                  output: "schemathesis-results.json"
                  verbose: true
                
                # Filtering
                filters:
                  - "not (method == 'OPTIONS')"
                  - "not (path == '/health')"
                
                # Custom checks
                checks:
                  - "not_a_server_error"
                  - "status_code_conformance"
                  - "content_type_conformance"
                  - "response_schema_conformance"
                  - "response_headers_conformance"
                """.formatted(baseUrl);

        Files.write(Paths.get(outputDir, "schemathesis.yaml"), config.getBytes());
    }

    /**
     * Generate test execution script
     */
    private void generateTestExecutionScript(Map<String, Object> spec, String outputDir, String baseUrl) throws IOException {
        String script = """
                #!/bin/bash
                
                set -e
                
                echo "Starting Schemathesis tests..."
                echo "Base URL: %s"
                echo "OpenAPI spec: openapi.yaml"
                
                # Check if Schemathesis is installed
                if ! command -v schemathesis &> /dev/null; then
                    echo "Installing Schemathesis..."
                    pip install schemathesis
                fi
                
                # Run basic tests
                echo "Running basic API tests..."
                schemathesis run openapi.yaml --base-url=%s --workers=4 --max-failures=10 --max-response-time=5000 --max-requests=1000 --format=json --output=basic-test-results.json
                
                # Run stateful tests
                echo "Running stateful API tests..."
                schemathesis run openapi.yaml --base-url=%s --stateful=links --workers=2 --max-failures=5 --format=json --output=stateful-test-results.json
                
                # Run negative tests
                echo "Running negative API tests..."
                schemathesis run openapi.yaml --base-url=%s --negative --workers=2 --max-failures=5 --format=json --output=negative-test-results.json
                
                # Run performance tests
                echo "Running performance tests..."
                schemathesis run openapi.yaml --base-url=%s --workers=8 --max-requests=5000 --max-response-time=2000 --format=json --output=performance-test-results.json
                
                # Generate report
                echo "Generating test report..."
                schemathesis report basic-test-results.json stateful-test-results.json negative-test-results.json performance-test-results.json --output=test-report.html

                # Display API endpoint coverage summary
                echo ""
                echo "=============================================="
                echo "       API Endpoint Coverage Summary"
                echo "=============================================="
                if [ -f basic-test-results.json ]; then
                    total=$(python3 -c "
                import json, sys
                try:
                    data = json.load(open('basic-test-results.json'))
                    stats = data.get('statistics', data.get('stats', {}))
                    total = stats.get('total', 'N/A')
                    passed = stats.get('passed', stats.get('success', 'N/A'))
                    failed = stats.get('failed', stats.get('failures', 'N/A'))
                    errors = stats.get('errors', stats.get('error', 'N/A'))
                    print(f'Total tests executed: {total}')
                    print(f'Passed:               {passed}')
                    print(f'Failed:               {failed}')
                    print(f'Errors:               {errors}')
                    if isinstance(total, int) and total > 0 and isinstance(passed, int):
                        pct = (passed / total) * 100
                        print(f'Pass rate:            {pct:.1f}%%')
                except Exception as e:
                    print(f'Could not parse results: {e}')
                " 2>/dev/null)
                    echo "$total"
                else
                    echo "No results file found."
                fi
                echo "=============================================="
                echo "HTML report: test-report.html"
                echo "=============================================="

                echo "Schemathesis tests completed!"
                echo "Results saved to: test-report.html"
                """.formatted(baseUrl, baseUrl, baseUrl, baseUrl, baseUrl);

        Files.write(Paths.get(outputDir, "run-schemathesis-tests.sh"), script.getBytes());

        // Make script executable
        Path scriptPath = Paths.get(outputDir, "run-schemathesis-tests.sh");
        scriptPath.toFile().setExecutable(true);
    }

    /**
     * Generate Docker configuration for Schemathesis
     */
    private void generateDockerConfig(Map<String, Object> spec, String outputDir, String baseUrl) throws IOException {
        // Generate Dockerfile
        String dockerfile = """
                FROM python:3.9-slim
                
                # Install system dependencies
                RUN apt-get update && apt-get install -y \\
                    curl \\
                    && rm -rf /var/lib/apt/lists/*
                
                # Install Schemathesis
                RUN pip install schemathesis
                
                # Set working directory
                WORKDIR /app
                
                # Copy OpenAPI spec
                COPY openapi.yaml .
                
                # Copy test configuration
                COPY schemathesis.yaml .
                
                # Copy test script
                COPY run-schemathesis-tests.sh .
                RUN chmod +x run-schemathesis-tests.sh
                
                # Set environment variables
                ENV BASE_URL=%s
                
                # Run tests
                CMD ["./run-schemathesis-tests.sh"]
                """.formatted(baseUrl);

        Files.write(Paths.get(outputDir, "Dockerfile"), dockerfile.getBytes());

        // Generate docker-compose.yml
        String dockerCompose = """
                version: '3.8'
                
                services:
                  schemathesis:
                    build: .
                    volumes:
                      - ./openapi.yaml:/app/openapi.yaml:ro
                      - ./schemathesis.yaml:/app/schemathesis.yaml:ro
                      - ./results:/app/results
                    environment:
                      - BASE_URL=%s
                    command: ./run-schemathesis-tests.sh
                """.formatted(baseUrl);

        Files.write(Paths.get(outputDir, "docker-compose.yml"), dockerCompose.getBytes());
    }

    /**
     * Generate CI/CD integration
     */
    private void generateCICDIntegration(Map<String, Object> spec, String outputDir, String baseUrl) throws IOException {
        // Generate GitHub Actions workflow
        String githubActions = """
                name: Schemathesis API Testing
                
                on:
                  push:
                    branches: [ main, develop ]
                  pull_request:
                    branches: [ main ]
                  schedule:
                    - cron: '0 2 * * *'  # Run daily at 2 AM
                
                jobs:
                  schemathesis-tests:
                    runs-on: ubuntu-latest
                
                    steps:
                    - uses: actions/checkout@v3
                
                    - name: Set up Python
                      uses: actions/setup-python@v4
                      with:
                        python-version: '3.9'
                
                    - name: Install Schemathesis
                      run: |
                        pip install schemathesis
                
                    - name: Run Schemathesis tests
                      run: |
                        schemathesis run openapi.yaml --base-url=%s --workers=4 --max-failures=10 --format=json --output=test-results.json
                
                    - name: Upload test results
                      uses: actions/upload-artifact@v3
                      if: always()
                      with:
                        name: schemathesis-results
                        path: test-results.json
                
                    - name: Comment PR with results
                      uses: actions/github-script@v6
                      if: github.event_name == 'pull_request'
                      with:
                        script: |
                          const fs = require('fs');
                          const results = JSON.parse(fs.readFileSync('test-results.json', 'utf8'));
                          const comment = `## Schemathesis Test Results
                
                          - **Total tests**: ${results.statistics.total}
                          - **Passed**: ${results.statistics.passed}
                          - **Failed**: ${results.statistics.failed}
                          - **Errors**: ${results.statistics.errors}
                
                          [View detailed results](${results.report_url})`;
                
                          github.rest.issues.createComment({
                            issue_number: context.issue.number,
                            owner: context.repo.owner,
                            repo: context.repo.repo,
                            body: comment
                          });
                """.formatted(baseUrl);

        Files.write(Paths.get(outputDir, "github-actions.yml"), githubActions.getBytes());

        // Generate Jenkins pipeline
        String jenkinsPipeline = """
                pipeline {
                    agent any
                
                    stages {
                        stage('Checkout') {
                            steps {
                                checkout scm
                            }
                        }
                
                        stage('Setup') {
                            steps {
                                sh 'pip install schemathesis'
                            }
                        }
                
                        stage('Run Schemathesis Tests') {
                            steps {
                                sh './run-schemathesis-tests.sh'
                            }
                        }
                
                        stage('Publish Results') {
                            steps {
                                publishTestResults testResultsPattern: 'test-results.json'
                            }
                        }
                    }
                
                    post {
                        always {
                            archiveArtifacts artifacts: 'test-results.json', fingerprint: true
                        }
                    }
                }
                """;

        Files.write(Paths.get(outputDir, "Jenkinsfile"), jenkinsPipeline.getBytes());
    }
}
