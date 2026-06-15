# Preference Protocol

## Purpose

This protocol separates durable system rules, reusable user preferences,
project-specific rules, and temporary task instructions.

Do not put every user request into core protocols. Core protocols should contain
only rules that are broadly necessary for RepoMind OS to work across projects.

## Rule Categories

- `CORE_SYSTEM_RULE`: a general RepoMind OS rule that belongs in kernel
  protocols. Use rarely.
- `USER_GLOBAL_PREFERENCE`: a cross-project user habit that should apply across
  many repositories.
- `USER_ROLE_PREFERENCE`: a long-lived user habit for one role only.
- `PROJECT_SPECIFIC_RULE`: a rule or constraint that applies only to the
  current project.
- `TASK_LOCAL_INSTRUCTION`: an instruction for the current task or packet only.
- `DO_NOT_STORE`: content that must not be written into repository files.

## Storage Locations

- Core system rules: kernel files such as `BOOT.md`, `CONTEXT_INDEX.md`, or
  protocol files.
- User global preferences: `user_preferences/GLOBAL.md`.
- User role preferences:
  `user_preferences/role_overrides/<ROLE>.md`.
- Project-specific rules: `PROJECT_STATE.md`, `roles/*.md`,
  `handoff/CURRENT.md`, or `decisions/`.
- Task-local instructions: keep only in the current chat or packet.
- Do-not-store content: do not write it into RepoMind OS files.

## Preference Entry Format

Use this structure for durable preference entries:

```text
- scope: USER_GLOBAL_PREFERENCE | USER_ROLE_PREFERENCE
- rule: <the reusable user preference>
- source: <how this was confirmed, without raw chat transcript>
- status: active | needs_review | superseded | rejected
- applies_to: <all roles, one role, or a task class>
- example: <short generic example, if useful>
- revisit_trigger: <when to review or remove this preference>
```

## Classification Rules

- If it should apply across many projects, classify it as
  `USER_GLOBAL_PREFERENCE`.
- If it should apply only to one role, classify it as `USER_ROLE_PREFERENCE`.
- If it depends on this repository, classify it as `PROJECT_SPECIFIC_RULE`.
- If it only affects the current request, classify it as
  `TASK_LOCAL_INSTRUCTION`.
- If it contains secrets, private details, raw chat, or unsafe claims, classify
  it as `DO_NOT_STORE`.
- If unsure, ask the user before writing durable preference files.

## Approval Rules

- Preference updates require user confirmation.
- If the user explicitly says "always do this", "for future projects", or
  equivalent, the Project Governor / Main Brain may propose a preference
  writeback.
- A specialist role may recommend a preference update, but should return it in
  a Role Result Packet instead of writing it directly.

## Forbidden Preference Content

Do not store:

- secrets, tokens, API keys, passwords, or credentials;
- raw private chat transcripts;
- unnecessary personal information;
- unverified conclusions presented as truth;
- project facts that belong in project state;
- temporary task instructions;
- preferences that would conflict with security, correctness, or user approval.
