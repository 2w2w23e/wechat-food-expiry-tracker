# Reviewer Preferences

Use this file for durable user preferences that apply only to the Reviewer role.

## Entry Template

```text
- scope: USER_ROLE_PREFERENCE
- rule: <Reviewer-specific preference>
- source: <user confirmation summary, not raw chat>
- status: active | needs_review | superseded | rejected
- applies_to: REVIEWER
- example: <short generic example>
- revisit_trigger: <when to review or remove>
```
