# Context Index

## Purpose

This file is the smart context router for RepoMind OS.
It tells GPT windows and Codex which governance files to read for a task.

The rule is minimum sufficient context. The goal is not to read the most files;
the goal is to read the right files.

## General Routing Rules

- Start with `BOOT.md`.
- Use this index to choose task-specific context.
- Read project files only when the task requires repository evidence.
- Do not treat this index as project state; it is a routing table.
- If this index is stale, propose an update and ask for approval before relying
  on the new route.

## Task Routes

### Project Intake

Read:

- `BOOT.md`
- `FIRST_WINDOW_PROTOCOL.md`
- `PROJECT_INTAKE.md`
- `PROJECT_STATE.md`
- repository README or project docs, if present

Use when starting RepoMind OS for a new or existing project.

### Existing Context Import

Read:

- `BOOT.md`
- `CONTEXT_IMPORT_PROTOCOL.md`
- `PROJECT_STATE.md`
- `handoff/CURRENT.md`
- `memory/INDEX.md`
- relevant files under `decisions/`
- relevant files under `roles/`, only if role prompts are being imported

Use when the user provides prior GPT summaries, old prompts, old roles, Codex
reports, project plans, README files, PR records, or similar sources.

### Daily Role Communication

Read:

- `BOOT.md`
- `COMMUNICATION_PROTOCOL.md`
- `PREFERENCE_PROTOCOL.md`, if the packet involves user preferences
- `prompts/role_task_packet.md`
- `prompts/role_result_packet.md`
- current role file, if a specialist role is involved
- `handoff/CURRENT.md`, if current status is needed

Use when the Project Governor / Main Brain sends task packets to role windows
or receives result packets through the user.

### Repository Writeback

Read:

- `BOOT.md`
- `WRITEBACK_PROTOCOL.md`
- `PREFERENCE_PROTOCOL.md`, if the writeback concerns user preferences
- `prompts/writeback_packet.md`
- `checklists/writeback.md`
- target writeback files

Use when temporary chat results, role outputs, decisions, handoff notes, or
memory updates may need durable repository storage.

### User Preference Update

Read:

- `BOOT.md`
- `PREFERENCE_PROTOCOL.md`
- `WRITEBACK_PROTOCOL.md`
- `user_preferences/README.md`
- `user_preferences/GLOBAL.md`, if the preference is global
- `user_preferences/role_overrides/<ROLE>.md`, if the preference is role-specific

Use when deciding whether a user request should become a cross-project
preference, a role-specific preference, a project rule, or a task-local
instruction.

### Role Creation

Read:

- `BOOT.md`
- `ROLE_CREATION_PROTOCOL.md`
- `FIRST_WINDOW_PROTOCOL.md`, if still in bootstrap
- `PROJECT_STATE.md`
- `roles/README.md`
- `roles/ROLE_TEMPLATE.md`
- relevant existing role files

Use when proposing, approving, creating, merging, splitting, or deprecating a
role.

### Codex Task

Read:

- `BOOT.md`
- `WORKFLOW.md`
- `roles/CODEX.md`
- `PROJECT_STATE.md`
- `handoff/CURRENT.md`
- relevant decisions
- only the source files needed for the requested task

Use when preparing a concrete implementation, refactor, test, or repository
change for Codex.

### Review And Verification

Read:

- `BOOT.md`
- `roles/REVIEWER.md`
- `roles/VERIFIER.md`
- `checklists/review_gate.md`
- `PROJECT_STATE.md`
- relevant decisions
- the diff or files under review

Use when checking correctness, regression risk, security risk, or completion.

### Teaching And Explanation

Read:

- `BOOT.md`
- `roles/TEACHER.md`
- `PROJECT_STATE.md`
- the specific files, decisions, or memories being explained

Use when the user asks for explanation, onboarding, or conceptual teaching.

### Project Direction Decision

Read:

- `BOOT.md`
- `THINKING_PROTOCOL.md`
- `PROJECT_STATE.md`
- relevant decisions
- `memory/ANTI_PATTERNS.md`
- repository evidence relevant to the decision

Use when deciding scope, architecture, product direction, dependencies, security
posture, or governance changes.

### Memory Update

Read:

- `BOOT.md`
- `WRITEBACK_PROTOCOL.md`
- `THINKING_PROTOCOL.md`, if the update changes durable guidance
- `memory/INDEX.md`
- the relevant role memory file
- relevant decisions or handoff notes

Use when recording reusable lessons, recurring risks, project patterns, or
anti-patterns.

## Escalation

If the minimum context exposes uncertainty, read the next most relevant file and
state why it was added. Do not expand context silently.
