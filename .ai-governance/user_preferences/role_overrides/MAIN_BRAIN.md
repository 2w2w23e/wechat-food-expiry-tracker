# Main Brain Preferences

Use this file for durable user preferences that apply only to the Main Brain role.

## Active Preferences

- scope: USER_ROLE_PREFERENCE
- rule: MAIN_BRAIN and PROJECT_GOVERNOR should answer in Chinese by default. If explanatory English must be used, add a Chinese explanation immediately after it.
- source: User explicitly requested this as a future requirement for MAIN_BRAIN and PROJECT_GOVERNOR.
- status: active
- applies_to: MAIN_BRAIN, PROJECT_GOVERNOR
- example: Use “Role Result Packet（角色结果包）” rather than only “Role Result Packet”.
- revisit_trigger: User asks to change language policy.

- scope: USER_ROLE_PREFERENCE
- rule: For long Codex tasks, MAIN_BRAIN and PROJECT_GOVERNOR should route the task to CODEX_TASK_GENERATOR and provide the user with the prompt for that role, instead of directly writing the full long Codex task themselves.
- source: User explicitly requested this as a future requirement for MAIN_BRAIN and PROJECT_GOVERNOR.
- status: active
- applies_to: MAIN_BRAIN, PROJECT_GOVERNOR
- example: Provide a short Role Task Packet prompt for CODEX_TASK_GENERATOR to produce /goal and require.txt.
- revisit_trigger: User changes Codex task generation workflow.

- scope: USER_ROLE_PREFERENCE
- rule: For short Codex tasks, MAIN_BRAIN and PROJECT_GOVERNOR may provide the Codex task directly, but must explain why direct generation is acceptable.
- source: User explicitly requested this as a future requirement for MAIN_BRAIN and PROJECT_GOVERNOR.
- status: active
- applies_to: MAIN_BRAIN, PROJECT_GOVERNOR
- example: State that the task is small because it affects one file, has clear acceptance criteria, and does not cross version or security boundaries.
- revisit_trigger: User changes Codex task generation workflow.

- scope: USER_ROLE_PREFERENCE
- rule: MAIN_BRAIN and PROJECT_GOVERNOR should include a Beijing time timestamp with year, month, day, hour, and minute at the start of each answer.
- source: User explicitly requested timestamped future responses.
- status: active
- applies_to: MAIN_BRAIN, PROJECT_GOVERNOR
- example: 【北京时间：2026年06月19日 00:20】
- revisit_trigger: User asks to remove or change timestamp format.

## Entry Template

```text
- scope: USER_ROLE_PREFERENCE
- rule: <Main Brain-specific preference>
- source: <user confirmation summary, not raw chat>
- status: active | needs_review | superseded | rejected
- applies_to: MAIN_BRAIN
- example: <short generic example>
- revisit_trigger: <when to review or remove>
```
