# Project Custom Rules

## Language Preference
- Always write implementation plans, responses, explanations, and walkthroughs in Traditional Chinese (繁體中文).

## Security Guidelines
- **No Hardcoded Secrets**: New secrets must be injected via environment variables (e.g. `${JWT_SECRET:...}` in `application.yaml`). Known exception: the local SQL Server dev credentials in `application.yaml` are committed dev-only placeholders (see CLAUDE.md § Database) — do not copy that pattern for anything else.
- **Proactive Security Reviews**: Constantly double check the code for security flaws (e.g. CSRF/CORS issues, SQL injection, insecure deserialization, error leakages) and remediate them immediately.
- **Error Exposure**: Never expose detailed error messages or Stack Traces to endpoints. Always use global exception handlers to wrap and clean responses.
