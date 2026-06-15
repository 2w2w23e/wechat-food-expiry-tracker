# Roles

This directory stores role templates and approved role instructions for a
project using RepoMind OS.

RepoMind OS is not a fixed role framework. A user project does not need to
enable every role in this directory, and it should not create roles just to make
the system look complete.

## Recommended Startup Core

The recommended startup core is:

- `PROJECT_GOVERNOR.md`: coordinates project direction, approval, context
  routing, and writeback judgment.
- `REPO_GOVERNOR.md`: audits proposals against repository reality.

These roles help bootstrap safely. Other roles should be imported, generated,
split, merged, rewritten, activated, or rejected based on the current project's
needs.

## Role Creation Rule

Create or activate a role only when:

- the project need is clear;
- the scope and authority are bounded;
- overlap with existing roles has been reviewed;
- the user has approved the role.

Use `../ROLE_CREATION_PROTOCOL.md` before adding or changing durable role files.

## Minimal Setup Is Valid

A small project may use only the core protocols and a minimal set of roles.
Larger or riskier projects may add specialist roles when the Project Governor
and Repo Governor can justify them with repository evidence and user intent.
