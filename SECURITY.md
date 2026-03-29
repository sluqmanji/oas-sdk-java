# Security Policy

## Supported Versions

We actively support the following versions with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 2.x     | :white_check_mark: |
| 1.17.x  | :white_check_mark: |
| < 1.17  | :x:                |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security vulnerability, please follow these steps:

1. **Do NOT** create a public GitHub issue
2. Email security details to: security@egain.com
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if available)

## Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Fix Timeline**: Depends on severity, typically 30-90 days

## Security Best Practices

When using this SDK:

1. **Keep dependencies updated**: Regularly update to the latest version
2. **Validate inputs**: Always validate OpenAPI specifications before processing
3. **Secure file handling**: Use secure file paths, avoid path traversal vulnerabilities
4. **Logging**: Be cautious with sensitive data in logs
5. **Generated code**: Review generated code for security implications

## Known Security Considerations

- **File Path Resolution**: The SDK uses `PathResolver` to prevent path traversal attacks
- **External References**: External file references are resolved securely
- **Input Validation**: All OpenAPI specifications are validated before processing

## Security Updates

Security updates will be:
- Released as patch versions (e.g., 1.17.1)
- Documented in CHANGELOG.md
- Announced via GitHub releases

Thank you for helping keep OAS SDK secure!

