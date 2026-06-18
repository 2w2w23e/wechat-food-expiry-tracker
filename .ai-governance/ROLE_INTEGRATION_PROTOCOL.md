# Role Integration Protocol

## Purpose

This protocol defines Existing Role Integration Mode.

Use it when a project already has role files, old prompts, agent rules, role-specific user preferences, working habits, or another AI governance system.

Core rule:

```text
Existing roles are project assets, not obstacles.
```

RepoMind OS must discover, classify, and evaluate existing roles before it creates or activates RepoMind default roles.

## When This Protocol Applies

Use this protocol before project testing, Codex execution, code-change recommendations, or role creation when any of these are present:

- existing role files;
- old GPT or AI prompts;
- `AGENTS.md` or agent instructions;
- AI rules, workflow rules, or governance notes;
- role-specific user preferences or working habits that define role behavior;
- Codex reports that describe role behavior;
- imported project governance summaries.

If imported context contains role or prompt behavior, route it here from `CONTEXT_IMPORT_PROTOCOL.md`.

Ordinary style preferences should use `PREFERENCE_PROTOCOL.md`.

## Existing Role Discovery

Discover first. Do not rewrite first.

For each discovered role-like source, record:

- source path or source description;
- source type;
- intended user or owner, if known;
- freshness, if known;
- responsibilities;
- authority limits;
- inputs and outputs;
- safety boundaries;
- overlap with RepoMind OS roles;
- uncertainty and missing evidence.

## Role Source Classification

Classify by behavior, not by name.

Evaluate responsibilities, authority, allowed writes, required inputs, expected outputs, approval requirements, memory or context dependencies, handoff rules, and risks from stale, broad, or unsafe instructions.

Do not merge roles only because their names look similar.

## Role Compatibility Actions

Only four compatibility actions are allowed:

- `preserve`: keep the role as an active project asset.
- `wrap`: keep the original role text and add a RepoMind OS adapter layer.
- `merge`: combine overlapping responsibilities into an approved target role.
- `deprecate / suspend`: keep the source readable but mark it inactive unless the user approves deletion.

Prefer `preserve` or `wrap` when the old role has useful project knowledge.

Use `merge` only when responsibilities, authority, inputs, outputs, or risk boundaries overlap enough to justify it.

Do not overwrite a valuable old role merely to fit the RepoMind OS template.

## Role Adapter Layer

When an existing role is preserved or wrapped, add or draft an adapter layer with at least these fields:

```text
Role Identity
Original Source
Owns
Does Not Own
Required Read Order
Long-term Memory Lookup
Experience / Anti-pattern Lookup
Answer Header Requirement
Writeback Rules
Codex Boundary
Stop Conditions
```

The adapter layer supplies missing RepoMind OS boundaries without deleting useful old content.

## Required Read Order Upgrade

Each active role must know which repository files to read before each substantive answer.

At minimum, an active role needs:

- its own role file or adapter;
- `BOOT.md`;
- `CONTEXT_INDEX.md`;
- current project state and intake files when relevant;
- `handoff/CURRENT.md` when current status matters;
- task-specific decisions, memory, anti-patterns, or role-specific user preferences.

The read order must be explicit before the role is activated.

## Long-term Memory and Experience Lookup Upgrade

Each active role must know where to look before repeated or risky questions.

The lookup rules must cover:

- `PROJECT_STATE.md`;
- `PROJECT_INTAKE.md`;
- `handoff/CURRENT.md`;
- relevant `memory/*`;
- relevant `decisions/*`;
- relevant `anti_patterns/*`;
- relevant role-specific `user_preferences/*`.

If no memory file is read for an answer, the role must say so in the Context Refresh Header.

If the memory path was checked and empty, the role must say it was checked but empty.

Useful experience from an old role should become a role adapter, memory draft, decision draft, or anti-pattern draft. It must not be silently lost.

## Conflict Detection

Detect conflicts before recommending execution.

Conflicts include:

- two roles claiming the same decision authority;
- a role allowing writes that RepoMind OS requires user approval for;
- stale instructions contradicting current project state;
- prompts that bypass context refresh;
- prompts that allow automatic commit, push, release, or broad file edits;
- instructions that treat chat-only material as durable repository truth;
- missing stop conditions or Codex boundaries.

Do not resolve conflicts silently. Produce a draft recommendation and ask the user to approve the compatibility action.

## Role Registry Draft

Before writeback, produce a role registry draft.

The draft should include:

- source;
- proposed active role name;
- compatibility action;
- owns;
- does not own;
- required read order;
- long-term memory lookup;
- Codex boundary;
- risks;
- user decision needed.

This registry is a draft until the user approves durable writeback.

## User Approval Gate

Before explicit user approval, RepoMind OS must not:

- create role files;
- delete role files;
- rewrite role files;
- merge role files;
- rename role files;
- replace old roles with RepoMind default roles;
- mark a role active or inactive in durable state.

Before approval, only drafts are allowed.

If the user says "continue" while integration is incomplete, continue the role integration work. Do not route to implementation, testing, validation, or Codex execution.

## Stop Conditions

Stop and ask the user before proceeding when:

- an existing role or prompt has not been classified;
- a compatibility action would change, merge, suspend, or delete a role;
- a role appears unsafe or conflicts with user approval requirements;
- Required Read Order or Long-term Memory Lookup is missing for an active role;
- the user asks for execution before Foundation Complete is reached;
- durable writeback is needed but not approved.
