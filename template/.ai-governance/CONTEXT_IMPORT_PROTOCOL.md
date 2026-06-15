# Context Import Protocol

## Purpose

This protocol imports prior context without letting old or unverified material
silently become project truth.

Accepted sources include GPT chat summaries, old prompts, old role files, Codex
reports, project plans, README files, PR records, issue discussions, release
notes, and user-provided notes.

## Core Rule

Imported context is evidence, not fact.

It becomes durable project state only after verification against the repository
or explicit user confirmation.

## Import Workflow

1. Identify each source
   - Record source type, approximate date if known, author if relevant, and
     confidence level.

2. Classify the content
   - Project facts
   - Product intent
   - Current implementation claims
   - Historical decisions
   - Role prompts or role behavior
   - Reusable experience
   - Stale or suspicious content
   - Forbidden content

3. Verify or confirm
   - Verify implementation claims against repository files.
   - Ask the user to confirm product intent, unresolved decisions, and role
     authority.
   - Mark stale or suspicious content instead of merging it into state.

4. Route to the correct destination
   - `PROJECT_STATE.md`: current verified facts and confirmed project direction.
   - `decisions/`: approved historical or new decisions.
   - `memory/`: reusable lessons, recurring risks, and anti-patterns.
   - `roles/`: approved role prompts only after `ROLE_CREATION_PROTOCOL.md`.
   - `handoff/`: current work status, unresolved questions, and import summary.
   - `CONTEXT_INDEX.md`: routing pointers, not raw facts.

## Classification Rules

- Project facts require repository evidence or user confirmation.
- Product intent requires user confirmation unless it is already documented in a
  trusted project artifact.
- Current implementation claims must be checked against the current repository.
- Historical decisions need a source and status: accepted, superseded, rejected,
  or uncertain.
- Role prompts are proposals until approved through the role creation protocol.
- Reusable experience may enter memory only when it is generalized and useful
  beyond one chat message.
- Stale or suspicious content should remain labeled and should not drive action.

## Forbidden Content

Do not store:

- secrets, tokens, API keys, passwords, or credentials;
- raw private chat transcripts;
- unnecessary personal information;
- confidential external content the repository should not contain;
- unverified claims that could cause unsafe implementation decisions;
- large copied materials when a short summary is sufficient.

## User Confirmation Required

Ask the user before importing:

- project goals or product direction;
- decisions that affect architecture, security, or scope;
- role authority or role files;
- sensitive operational details;
- any content whose source or freshness is unclear.
