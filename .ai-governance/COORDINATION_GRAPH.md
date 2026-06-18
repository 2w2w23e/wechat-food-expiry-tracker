# Coordination Graph

## Purpose

RepoMind OS can borrow graph-based orchestration patterns without adding a
graph orchestration runtime, package, CLI, or dependency.

It coordinates human-in-the-loop work through repository files, GPT role
windows, Codex execution tasks, explicit packets, and user approval gates.

## Core Mapping

```text
Shared State = repository governance files
Nodes = GPT role windows and Codex
Edges = Role Task Packet, Role Result Packet, Writeback Packet
Router = Project Governor and CONTEXT_INDEX.md
Checkpoint = handoff/CURRENT.md
Long-term Store = memory/*, decisions/*, user_preferences/*
Human Gate = explicit user approval
```

## Shared State

Shared state is the approved repository governance file set. It includes current
project state, intake, handoff, role files, decisions, memory, context routes,
and user preferences.

In a GitHub-hosted repository, these governance files are the GitHub-as-state
coordination layer.

Chat content is not shared state until it is summarized, verified or confirmed,
approved by the user, and written through the proper writeback protocol.

## Nodes

- Project Governor: router and orchestrator node.
- Specialist GPT role windows: analysis, review, planning, or domain nodes.
- Codex: execution node for bounded repository changes.
- User: human approval gate and packet carrier, not an automated worker node.

Codex is not a direction decision maker. It executes only after the route,
scope, allowed files, forbidden files, validation, and approval status are clear.

## Edges

- Role Task Packet = edge input.
- Role Result Packet = edge output.
- Writeback Packet = state mutation request.

Edges must carry enough context for the receiving node to work without hidden
chat history.

## Checkpoints

`handoff/CURRENT.md` is the coordination checkpoint. It records current status,
next route, unresolved questions, and what another window or Codex task needs
to resume safely.

## Long-term Store

Long-term store includes:

- `memory/*`
- `decisions/*`
- `user_preferences/*`

Use long-term store for durable lessons, approved decisions, and reusable user
preferences. Do not store secrets, raw private chat, or unverified claims as
truth.

## Human Approval Gates

Explicit user approval is required before:

- durable writeback;
- role creation, merge, deletion, deprecation, suspension, or material rewrite;
- Codex execution with repository changes;
- direction changes that affect scope, architecture, security, or role
  authority.

A user answering questions is not approval to write durable state.

A user saying "continue" is not approval to enter execution state.

## Bootstrap State Machine

Bootstrap states:

```text
BOOTSTRAP_NOT_STARTED
CONTEXT_REFRESHED
INTAKE_DRAFTED
EXISTING_GOVERNANCE_CHECKED
ROLE_INTEGRATION_DRAFTED
ROLE_FOUNDATION_DRAFTED
USER_APPROVED_FOUNDATION
FOUNDATION_COMPLETE
EXECUTION_ALLOWED
```

Before `FOUNDATION_COMPLETE`, do not enter testing, implementation, Codex
execution, stability validation, or refactor planning.

## Allowed Transitions

```text
BOOTSTRAP_NOT_STARTED -> CONTEXT_REFRESHED
CONTEXT_REFRESHED -> INTAKE_DRAFTED
INTAKE_DRAFTED -> EXISTING_GOVERNANCE_CHECKED
EXISTING_GOVERNANCE_CHECKED -> ROLE_INTEGRATION_DRAFTED or ROLE_FOUNDATION_DRAFTED
ROLE_INTEGRATION_DRAFTED -> ROLE_FOUNDATION_DRAFTED
ROLE_FOUNDATION_DRAFTED -> USER_APPROVED_FOUNDATION
USER_APPROVED_FOUNDATION -> FOUNDATION_COMPLETE
FOUNDATION_COMPLETE -> EXECUTION_ALLOWED
```

## Forbidden Transitions

```text
INTAKE_DRAFTED -> EXECUTION_ALLOWED
CONTEXT_REFRESHED -> Codex execution
ROLE_INTEGRATION_DRAFTED -> testing
ROLE_FOUNDATION_DRAFTED without user approval -> durable writeback
Any state before FOUNDATION_COMPLETE -> implementation / testing / refactor planning
```

Do not skip intermediate foundation states for convenience.

## Router Rules

- Use `CONTEXT_INDEX.md` to choose the minimum sufficient files for the route.
- The Project Governor reports the current coordination state before major
  routing.
- The Project Governor reports the allowed next transition.
- If the user asks to execute before `FOUNDATION_COMPLETE`, advance only the
  next incomplete foundation state.
- If the next transition is unclear, stop and ask the user to choose or approve
  the route.

## Codex Execution Boundary

Codex may enter only after `EXECUTION_ALLOWED`.

Before routing to Codex, the packet must include:

- current coordination state;
- approved objective;
- allowed files;
- forbidden files;
- validation commands;
- stop conditions;
- writeback expectation;
- approval status.

## Stop Conditions

Stop before routing or state mutation when:

- the current coordination state is unknown;
- the requested next state is not an allowed transition;
- user approval is missing for durable writeback or role changes;
- Codex execution is requested before `EXECUTION_ALLOWED`;
- required packet context is missing;
- shared state would be updated from unverified or private chat content.
