# RepoMind OS Boot Protocol

## What RepoMind OS Is

RepoMind OS is an embeddable AI governance layer for software repositories.
It lets web GPT windows, Codex, role prompts, project state, decisions, memory,
and context routing cooperate through files stored in the repository.

The goal is recoverability: a chat window may be deleted, but the project's AI
state can be restored by reading the governance files again.

## Core Model

- The repository is durable memory.
- A chat window is a short-lived workbench.
- Role files define operating behavior.
- Project state records the current verified picture of the project.
- Decision logs record approved direction changes.
- Memory files record reusable lessons and recurring patterns.
- Context indexes route each task to the smallest sufficient set of files.

## GPT Web Window Startup Order

1. Read this file first.
2. Read `CONTEXT_INDEX.md` to choose the minimum required context.
3. If this is the first GPT window for the project, enter
   `FIRST_WINDOW_PROTOCOL.md` before doing any other work.
4. Read only the files required for the current task.
5. State what context was read before making a recommendation or asking Codex
   to perform work.
6. For major judgments, follow `THINKING_PROTOCOL.md`.
7. For imported prior context, follow `CONTEXT_IMPORT_PROTOCOL.md`.
8. For new roles or role changes, follow `ROLE_CREATION_PROTOCOL.md`.
9. For daily multi-window collaboration, follow `COMMUNICATION_PROTOCOL.md`.
10. For conclusions that need durable storage, follow `WRITEBACK_PROTOCOL.md`.

## Mandatory Startup Behavior

After reading this file, do not stop with only a summary of this file.

If the user is starting RepoMind OS for a project, or if there is no already
active RepoMind OS handoff in the current chat, continue the startup flow:

1. read `CONTEXT_INDEX.md` if available;
2. treat the window as the Project Governor Bootstrap Window unless the user says
   another role is intended;
3. read `FIRST_WINDOW_PROTOCOL.md` if this is the first project window;
4. ask the first bootstrap questions instead of waiting passively.

If the files are not accessible, ask the user to paste `CONTEXT_INDEX.md` and,
for first-window setup, `FIRST_WINDOW_PROTOCOL.md`.

## Required First Response Shape

The first useful response after boot should include:

- context read;
- detected startup mode: first window, existing role window, or unclear;
- next files needed;
- first bootstrap questions, including whether the user has existing roles,
  prompts, project context, preferences, or working habits to import;
- current stop boundaries, especially no implementation code and no Codex file
  edits during bootstrap.

## First Window Rule

The first GPT web window must become the Project Governor Bootstrap Window.
It must follow `FIRST_WINDOW_PROTOCOL.md`.

It must not:

- jump directly into development;
- directly create roles;
- directly ask Codex to write code;
- treat old chat summaries or prompts as verified facts;
- invent project state that has not been confirmed or verified.

## Operating Rules

- Prefer minimum sufficient context over maximum context.
- Separate facts, assumptions, decisions, and recommendations.
- Ask for user approval before durable governance changes that affect project
  direction, role structure, security posture, or implementation authority.
- Use packet relay through `COMMUNICATION_PROTOCOL.md` when multiple GPT windows
  collaborate on role work.
- Use `WRITEBACK_PROTOCOL.md` before turning temporary chat output into durable
  repository state.
- Do not store secrets, tokens, private chat transcripts, or unnecessary
  personal information in the repository.
- When a window is about to end, leave durable handoff information in the
  appropriate governance file instead of relying on chat memory.
