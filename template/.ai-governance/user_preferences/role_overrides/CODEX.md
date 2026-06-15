# Codex Preferences

Use this file for durable user preferences that apply only to Codex task
execution and reporting.

## Entry Template

```text
- scope: USER_ROLE_PREFERENCE
- rule: <Codex-specific preference>
- source: <user confirmation summary, not raw chat>
- status: active | needs_review | superseded | rejected
- applies_to: CODEX
- example: <short generic example>
- revisit_trigger: <when to review or remove>
```
