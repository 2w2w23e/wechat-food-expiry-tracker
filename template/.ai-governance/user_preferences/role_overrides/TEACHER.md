# Teacher Preferences

Use this file for durable user preferences that apply only to the Teacher role.

## Entry Template

```text
- scope: USER_ROLE_PREFERENCE
- rule: <Teacher-specific preference>
- source: <user confirmation summary, not raw chat>
- status: active | needs_review | superseded | rejected
- applies_to: TEACHER
- example: <short generic example>
- revisit_trigger: <when to review or remove>
```
