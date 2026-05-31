
# AGENTS.md

## Project

This repository contains a WeChat Mini Program named “食期管家”, a food expiry tracker for recording food production date, shelf life, final edible date, storage method, quantity, category, notes, sorting, reminders, and future OCR recognition.

## Product principles

- MVP first: manual entry, date calculation, sorting, storage method, and basic reminders should be stable before advanced OCR or AI features.

-`expiryDate` is the canonical final edible date and must be used for sorting and reminder logic.

- Users may create `expiryDate` in two ways:

  - production date + shelf life -> calculated expiry date
  - direct manual input of final edible date
- OCR or AI recognition results must never be saved directly without user confirmation.
- The app manages food label information and reminders. Do not make medical, safety, or nutrition claims.

## Tech assumptions

- Mini Program frontend: WXML, WXSS, JavaScript or TypeScript.
- Recommended backend: WeChat Cloud Development / CloudBase.
- Data storage: cloud database or local mock data during early MVP.
- Sensitive secrets such as OCR API keys, OpenAI keys, or cloud service credentials must never be stored in Mini Program client code.
- OCR, AI extraction, and secret-dependent logic should be implemented in cloud functions or server-side services.

## Expected repository structure

-`README.md`: human-readable project introduction.

-`AGENTS.md`: rules for Codex and AI coding assistants.

-`docs/project-brief.md`: project scope and MVP boundary.

-`docs/learning-map.md`: learning plan for the developer.

-`docs/decision-log.md`: key project decisions and reasons.

-`.agents/skills/miniapp-food-expiry/SKILL.md`: repository-level Codex skill.

-`miniprogram/`: Mini Program source code, when initialized.

-`cloudfunctions/`: cloud functions, when initialized.

## Coding rules

- Keep changes small and reviewable.
- Do not perform large refactors unless explicitly requested.
- Prefer adding or updating documentation when behavior changes.
- Keep date calculation logic isolated in a utility module when the Mini Program code is initialized.
- Keep OCR parsing logic separate from UI code.
- Keep cloud-function-only logic out of the Mini Program frontend.
- Do not introduce new production dependencies unless the reason is explained.

## Date calculation requirements

The app must support two date input modes:

1. Calculated mode:

   - input: production date + shelf life value + shelf life unit
   - output: final edible date stored as `expiryDate`
2. Manual mode:

   - input: user directly enters final edible date
   - output: final edible date stored as `expiryDate`

When changing date logic, consider:

- shelf life in days
- shelf life in months
- shelf life in years
- leap years
- month-end dates
- invalid or missing dates
- manual final edible date override
- opened date and after-open shelf life, if implemented

## OCR requirements

OCR flow must follow this order:

1. User chooses or takes a photo.
2. Image is processed by OCR.
3. Raw OCR text is shown or preserved for confirmation/debugging where appropriate.
4. Structured fields are extracted.
5. User reviews and edits the extracted fields.
6. Data is saved only after user confirmation.

Never auto-save OCR or AI extraction results directly to the food database.

## Reminder requirements

Reminder logic should be based on `expiryDate`.

Default reminder offsets may include:

- 7 days before expiry
- 3 days before expiry
- 1 day before expiry
- expiry day

When changing reminder logic, consider:

- user reminder switch
- expired items
- today-expiring items
- items without valid `expiryDate`
- subscription message authorization
- in-app reminder fallback

## Documentation requirements

When making meaningful changes, consider updating:

-`README.md`

-`docs/project-brief.md`

-`docs/decision-log.md`

- related feature documentation

## Before opening a pull request

Include:

1. Summary of changes
2. Files changed
3. Manual test steps
4. Screenshots for UI changes, when applicable
5. Risks or edge cases
6. Anything not covered by tests

## Out of scope unless explicitly requested

- Do not build OCR or AI auto-save flows.
- Do not add API keys or secrets to the Mini Program frontend.
- Do not make medical or food safety guarantees.
- Do not rewrite the whole project structure without approval.
- Do not mix multiple large features in one task.
