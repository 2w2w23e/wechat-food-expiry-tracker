# Project Intake

Last updated: 2026-06-15

## Intake Mode

Existing project intake.

RepoMind OS is being introduced after the project already has source code, product docs, AI collaboration docs, role prompts, Codex workflow docs, and recent merged PRs.

## Project Identity

Project name: 食期管家 / WeChat Food Expiry Tracker.

Repository: `2w2w23e/wechat-food-expiry-tracker`.

Project type: WeChat Mini Program application.

Primary users: household users, with an explicit preference for simple and elder-friendly flows.

## Product Purpose

The project helps users manage food inventory and expiry dates.

Users should be able to record food name, category, quantity, remaining quantity, production date, shelf life, final edible date, storage method, and notes.

The canonical date field is `expiryDate`, used for sorting, status display, and future reminder design.

## Confirmed Direction

The user approved a layered migration into RepoMind OS on 2026-06-15.

RepoMind OS should become the highest governance layer, while the previous `docs/*` AI collaboration documents remain as historical governance documents and import evidence until they are reviewed, migrated, superseded, or linked.

## Existing Context Sources

The following existing project artifacts are available as import evidence:

- `README.md`
- `docs/VERSION_ROADMAP.md`
- `docs/PHASE_STATUS.md`
- `docs/AI_COLLABORATION.md`
- `docs/ROLE_PROMPTS.md`
- `docs/CODEX_WORKFLOW.md`
- `docs/DOCUMENT_OWNERSHIP.md`
- `docs/TOOL_AND_SKILL_POLICY.md`
- `.agents/skills/miniapp-food-expiry/SKILL.md`
- merged GitHub PRs, especially PR #7

These sources are evidence, not automatically durable RepoMind OS state, unless verified against repository files or confirmed by the user.

## Bootstrap Preference

Setup mode: layered migration.

Do not immediately create a large role library.

First durable bootstrap target:

1. record verified current project state;
2. record the governance-layer decision;
3. record current handoff;
4. keep old docs available as evidence;
5. only then decide which roles or old docs should be migrated, linked, or deprecated.

## Current Boundaries

During bootstrap:

- do not write implementation code;
- do not ask Codex to modify source files;
- do not advance V1, cloud development, barcode, OCR, AI, or reminder implementation;
- do not store secrets, raw private chat transcripts, or unnecessary personal information;
- do not treat old role prompts as approved RepoMind OS role files until role migration is explicitly approved.
