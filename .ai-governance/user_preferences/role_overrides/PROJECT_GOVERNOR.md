# Project Governor Preferences

Use this file for durable user preferences that apply only to the Project
Governor role.

## Entry Template

```text
- scope: USER_ROLE_PREFERENCE
- rule: <Project Governor-specific preference>
- source: <user confirmation summary, not raw chat>
- status: active | needs_review | superseded | rejected
- applies_to: PROJECT_GOVERNOR
- example: <short generic example>
- revisit_trigger: <when to review or remove>
```
