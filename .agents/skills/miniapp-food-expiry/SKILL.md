---
name: miniapp-food-expiry
description: Use this skill when working on the 食期管家 WeChat Mini Program repository, especially tasks involving food expiry tracking, manual food entry, production date plus shelf life calculation, direct final edible date input, expiryDate sorting, storage methods, OCR confirmation flow, reminder logic, CloudBase cloud functions, cloud database permissions, documentation, tests, and Codex PR review. Do not use this skill for unrelated generic web apps or non-WeChat projects.
---

# Miniapp Food Expiry Workflow

## Purpose

Guide Codex when working on “食期管家”.

Current repository state: Android APK development is temporarily active under `apk/`; the accepted WeChat Mini Program V0 source is archived under `miniprogram/`. Do not modify `miniprogram/` unless the user explicitly reopens Mini Program work.

This project records food production date, shelf life, final edible date, storage method, notes, sorting, reminders, and future OCR recognition. Keep the MVP small and reliable before adding OCR or AI automation.

## First steps

1. Identify the task type:
   - project setup
   - documentation
   - UI page
   - data model
   - date calculation
   - food list and sorting
   - OCR flow
   - reminder logic
   - CloudBase cloud function
   - cloud database permission
   - test
   - bug fix
   - PR review

2. Read the relevant project context before changing code:
   - `README.md`
   - `AGENTS.md`
   - `docs/project-brief.md`
   - relevant implementation files under `apk/` or `miniprogram/`, depending on the requested platform

3. If these files exist, also read them when relevant:
   - `docs/PRD.md`
   - `docs/DATA_MODEL.md`
   - `docs/ARCHITECTURE.md`
   - `docs/OCR_SPEC.md`
   - `docs/REMINDER_SPEC.md`
   - `docs/TEST_PLAN.md`

4. Prefer small, reviewable changes.

5. Do not perform broad refactors unless the user explicitly requests them.

## Product invariants

- `expiryDate` is the canonical final edible date.
- Sorting and reminder logic must be based on `expiryDate`.
- Users can create `expiryDate` in two ways:
  - calculated mode: production date + shelf life
  - manual mode: user directly enters final edible date
- OCR and AI recognition results must go through a user confirmation screen before saving.
- Secrets must not be placed in Mini Program or Android client code.
- The app manages label information and reminders. It must not claim that food is medically or absolutely safe.

## Date calculation checklist

For any date calculation change, verify:

- production date + shelf life in days
- production date + shelf life in months
- production date + shelf life in years
- leap year cases
- month-end cases
- invalid or missing dates
- manual expiry date mode
- user override behavior
- opened date + after-open shelf life, if implemented
- ensure strict timezone handling (defaulting to GMT+8/China Standard Time) to prevent off-by-one-day errors when calculations cross midnight

Implementation guidance:

- Keep date calculation logic isolated in a utility module when the Mini Program code is initialized.
- Do not duplicate date calculation logic across pages.
- Store the final result in `expiryDate`.

## OCR checklist

For OCR-related changes, verify:

- image selection or photo capture does not require secrets in the frontend
- OCR or AI extraction logic runs in cloud functions or server-side services
- raw OCR text is always preserved in the component state for debugging, but only displayed in the UI if the OCR confidence score is below 80%
- date formats are normalized before display or saving
- shelf life units are normalized
- low-confidence fields are visibly editable
- user confirmation is required before saving
- OCR results are never directly inserted into the food database

## Reminder checklist

For reminder-related changes, verify:

- reminder logic uses `expiryDate`
- default reminder offsets are 7, 3, 1, and 0 days before expiry
- reminders are not scheduled when disabled
- expired items display correctly
- today-expiring items display correctly
- items without valid `expiryDate` must be sorted at the bottom of the list and display "No expiry date" in the UI
- subscription-message authorization is respected
- in-app reminder fallback remains available

## CloudBase and database checklist

For cloud database or cloud function changes, verify:

- user data is isolated by user identity, such as openid
- database permissions do not allow users to read or modify other users’ food records
- secrets are stored only in cloud function environment variables or secure server-side config
- frontend code does not include API keys, OCR keys, OpenAI keys, or other credentials
- cloud functions validate inputs before writing to the database
- If the provided code already contains hardcoded secrets or credentials in the frontend, refuse to proceed with feature work until you explain the vulnerability and provide a refactoring plan to move them to CloudBase secure configuration.

## Documentation checklist

When behavior changes, consider updating:

- `README.md`
- `docs/project-brief.md`
- `docs/decision-log.md`
- feature-specific docs if they exist

For major decisions, add an entry to:

- `docs/decision-log.md`

## Out of scope unless explicitly requested

Do not:

- rewrite the whole project structure
- mix multiple large features in one task
- add OCR auto-save behavior
- add secrets to Mini Program frontend code
- introduce new production dependencies without explaining why
- make medical, nutrition, or absolute food-safety claims
- modify unrelated files

## Output expectations

When implementing code, summarize:

1. What changed
2. Files changed
3. Behavior changes
4. Manual test steps
5. Edge cases covered
6. Edge cases not covered
7. Risks or follow-up work

When reviewing a PR or Codex change, check:

1. Whether `expiryDate` remains the source of truth
2. Whether OCR confirmation is preserved
3. Whether secrets stay out of frontend code
4. Whether the change is small and reviewable
5. Whether tests or manual verification steps are included
