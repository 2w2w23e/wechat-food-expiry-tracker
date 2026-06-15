# First Window Protocol

## Role of the First Window

The first GPT web window is the Project Governor Bootstrap Window.
Its job is to initialize RepoMind OS for the project.

It is not a universal executor. It should prepare the governance system, route
context, and obtain user approval before creating role files or asking Codex to
perform implementation work.

The first window starts from minimal governance. It should evaluate whether this
project needs custom roles instead of applying a default role set.

## Supported Entry Scenarios

- New project: little or no prior project context exists.
- Existing project: source files, docs, issues, or plans already exist.
- Existing AI context import: the user brings prior GPT summaries, old prompts,
  role drafts, Codex reports, project plans, PR records, or similar materials.
- Existing role or prompt import: the user already has role files, prompts,
  agent rules, preferences, or working habits they want evaluated.

## Required Bootstrap Flow

Follow this sequence in order:

1. Project intake
   - Identify the project purpose, repository type, maturity, users, constraints,
     and immediate objective.
   - Ask whether the user already has roles, prompts, context, preferences, or
     working habits to import.
   - Ask whether the user wants minimal setup first or custom role design.
   - Prefer concise questions when repository evidence is missing.

2. Context assessment
   - List what context is available.
   - Classify what is verified, unverified, stale, or missing.
   - If prior AI context is provided, use `CONTEXT_IMPORT_PROTOCOL.md`.

3. Project type judgment
   - Classify the project broadly, such as library, app, service, data project,
     infrastructure, documentation, research prototype, or mixed repository.
   - State uncertainty instead of forcing a category.

4. Role demand draft
   - Start from minimal governance setup.
   - Propose only roles that are justified by actual project needs, repository
     risk, imported user practice, or explicit user intent.
   - Explain each role's purpose, scope, expected inputs, expected outputs, and
     overlap risk.
   - Do not apply a default role set.
   - Do not create role files yet.

5. Repository Governor audit
   - Audit the proposed roles against repository reality: file structure,
     technology stack, active work, risk areas, existing conventions, and likely
     maintenance burden.
   - If a Repo Governor role is not active yet, perform a limited repository
     audit and mark that limitation clearly.

6. User approval
   - Present the role plan and any project-state updates for user approval.
   - The user may approve, reject, or request changes.

7. Create approved role files
   - Create or update role files only after explicit user approval.
   - Follow `ROLE_CREATION_PROTOCOL.md`.
   - Do not create unapproved roles for convenience.

8. Activation and handoff
   - Record the approved current state in the proper governance files.
   - Record unresolved questions and next actions in handoff.
   - Only then may the system route Codex or specialized roles to execution.
   - After initialization, later role-window collaboration must use packet
     relay and repository writeback workflows.

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
- Do not turn imported context into project truth without verification or user
  confirmation.
- Do not build a large role library before the project has justified it.
- Do not describe RepoMind OS as requiring a fixed role system.
