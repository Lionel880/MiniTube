# Project Custom Rules

## Language Preference
- Always write implementation plans, responses, explanations, and walkthroughs in Traditional Chinese (繁體中文).

## Security Guidelines
- **No Hardcoded Secrets**: All secrets are injected via environment variables (e.g. `${DB_PASSWORD:}`, `${JWT_SECRET:...}` in `application.yaml`). No hardcoded credentials anywhere in the repo — the previous dev-placeholder exception was removed on 2026-07-03 ahead of publishing to GitHub.
- **Proactive Security Reviews**: Constantly double check the code for security flaws (e.g. CSRF/CORS issues, SQL injection, insecure deserialization, error leakages) and remediate them immediately.
- **Error Exposure**: Never expose detailed error messages or Stack Traces to endpoints. Always use global exception handlers to wrap and clean responses.
