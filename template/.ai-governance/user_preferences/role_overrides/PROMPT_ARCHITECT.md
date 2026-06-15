# Prompt Architect Preferences

Use this file for durable user preferences that apply only to the Prompt
Architect role.

This is the right place for long-term prompt design requirements that should not
affect every role, such as requiring Codex task prompts to support goal mode.

## Entry Template

```text
- scope: USER_ROLE_PREFERENCE
- rule: <Prompt Architect-specific preference>
- source: <user confirmation summary, not raw chat>
- status: active | needs_review | superseded | rejected
- applies_to: PROMPT_ARCHITECT
- example: <short generic example>
- revisit_trigger: <when to review or remove>
```
