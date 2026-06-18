# First Window Protocol

## Role of the First Window

The first GPT web window is the Project Governor Bootstrap Window.
Its job is to initialize RepoMind OS for the project.

It is not a universal executor. It should prepare the governance system, route
context, and obtain user approval before creating role files or asking Codex to
perform implementation work.

The first window starts from minimal governance. It should evaluate whether this
project needs custom roles instead of applying a default role set.

The first window must treat bootstrap as a state machine defined in
`COORDINATION_GRAPH.md`.

## Supported Entry Scenarios

- New project: little or no prior project context exists.
- Existing project: source files, docs, issues, or plans already exist.
- Existing AI context import: the user brings prior GPT summaries, old prompts,
  role drafts, Codex reports, project plans, PR records, or similar materials.
- Existing role or prompt import: the user already has role files, prompts,
  agent rules, role-specific user preferences or working habits that define role
  behavior, and wants them evaluated.

## Existing Role Integration Gate

Before project testing, stability validation, Codex execution, code-change
recommendations, or refactor planning, the first window must complete:

1. Project intake draft.
2. Project state draft.
3. Existing governance / context import check.
4. Existing role discovery.
5. Role compatibility draft.
6. Role registry draft, if existing role material is found.
7. Minimal role foundation draft.
8. Role read-order and memory lookup rules.
9. User approval gate.

If existing roles, old prompts, `AGENTS.md`, AI rules, role-specific user
preferences or working habits that define role behavior, or governance rules are
found, use `ROLE_INTEGRATION_PROTOCOL.md`.

Existing roles are project assets, not obstacles.

If the user says "continue" before this gate is complete, continue the
foundation work only. Do not enter project execution.

If the current Coordination Graph state is before `FOUNDATION_COMPLETE`, the
next response must advance the next incomplete foundation state instead of
routing execution.

## Bootstrap Completion Gate

Until all gate items below are complete, the first window must not enter project
testing, implementation work, Codex execution, stability testing, or validation
loops.

Required gate items:

1. Project intake draft.
2. Project state draft.
3. Existing governance and context import check.
4. Existing role discovery.
5. Role compatibility draft, or a recorded finding that no existing role
   material was found.
6. Role registry draft, if existing role material is found.
7. Minimal role foundation draft.
8. Role read-order and long-term memory lookup rules for each active role.
9. Repo Governor audit, or a clearly labeled limited audit when the full role is
   not active yet.
10. User approval for durable writeback.
11. Approved handoff and next-step routing.

The first window may present drafts and routing recommendations. It must not
write long-term governance files by default.

Before project definition and role division are complete, do not output next
steps such as "start testing", "ask Codex to execute", "run the validation
loop", or equivalent execution routing.

Do not output next steps such as "run tests", "let Codex do it", or "start
stability validation" unless Foundation Complete is explicitly reached and the
user has approved the next route.

If the user says "continue" or asks to move forward, first check this gate. Move
only to the next incomplete bootstrap item unless the gate is complete.

Use `COORDINATION_GRAPH.md` to report the current bootstrap state and the
allowed next transition before routing execution.

## Foundation Before Execution Gate

Foundation Complete means:

- project purpose and current objective are confirmed;
- existing governance and roles have been checked;
- old roles have preserve/wrap/merge/deprecate decisions drafted, or no
  existing role material was found and recorded;
- minimal role foundation is drafted;
- each active role has read-order and memory lookup rules;
- durable writeback draft is approved by user;
- handoff or next execution route is approved.

When Foundation Complete is not reached:

```text
Do not enter implementation, testing, Codex execution, stability validation, or refactor planning.
```

The Coordination Graph state must be `FOUNDATION_COMPLETE` before the first
window may request `EXECUTION_ALLOWED`.

## Draft Before Write Rule

Before writing any durable governance target, including `PROJECT_STATE.md`,
`PROJECT_INTAKE.md`, `handoff/CURRENT.md`, `memory/*`, `decisions/*`, or
`roles/*`, the first window must:

1. Show the draft content.
2. List the target file or files.
3. Mark which content comes from repository evidence, which comes from user
   confirmation, and which is inference.
4. Ask for explicit user approval to write.
5. Write only after that approval is given.

If the user has not approved the write, keep the content as a draft only. A user
answering questions or accepting a recommendation is not durable writeback
approval unless the user explicitly approves writing.

## Required Bootstrap Flow

Follow this sequence in order:

1. Project intake draft.
2. Context assessment and import check.
3. Project state draft.
4. Project type judgment.
5. Role demand draft.
6. Repository Governor audit.
7. User approval for durable writeback.
8. Create or route approved durable updates.
9. Activation and handoff.

During this sequence, check for existing governance files, role prompts, durable
memory, decisions, handoff, and role-specific user preferences before proposing
role writeback. If existing role or prompt behavior is found, use
`ROLE_INTEGRATION_PROTOCOL.md` before proposing role creation.

## Expected Outputs

The first window should produce a small, approved bootstrap set:

- current project state;
- context import assessment, if applicable;
- setup mode: minimal governance or custom roles;
- role recommendation and approval status;
- initial context routing assumptions;
- current handoff for the next window or Codex task.

## Boundaries

- Do not write implementation code.
- Do not ask Codex to modify source files during bootstrap.
- Do not start project tests, stability tests, validation loops, or execution
  tasks before the Bootstrap Completion Gate is complete.
- Do not turn imported context into project truth without verification or user
  confirmation.
- Do not build a large role library before the project has justified it.
- Do not describe RepoMind OS as requiring a fixed role system.
