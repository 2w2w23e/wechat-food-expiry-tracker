# AGENTS.md

## Project

This repository contains a WeChat Mini Program named “食期管家”, a food expiry tracker that records production date, shelf life, final edible date, storage method, notes, OCR recognition, sorting, and reminders.

## Product principles

- MVP first: manual entry, date calculation, sorting, and reminders must be stable before advanced OCR automation.
- OCR results must never be saved without user confirmation.
- The final edible date is the source of truth for sorting and reminders.
- Do not make medical, safety, or nutrition claims. This app manages label information and reminders only.

## Tech assumptions

- Mini Program frontend: WXML, WXSS, JavaScript or TypeScript.
- Backend: WeChat Cloud Development / CloudBase cloud functions and database.
- Secrets for OCR or AI services must stay in cloud functions or server-side config, never in Mini Program client code.

## Expected repository structure

- `miniprogram/pages`: page implementations.
- `miniprogram/components`: reusable UI components.
- `miniprogram/services`: data and API service layer.
- `miniprogram/utils`: pure utility functions such as date calculation and parsing.
- `cloudfunctions`: cloud functions.
- `docs`: project documentation.

## Coding rules

- Keep date calculation logic in `miniprogram/utils/date.*` and test it independently.
- Keep OCR parsing logic in `miniprogram/utils/parser.*` or a cloud function if AI extraction is used.
- Avoid large rewrites unless explicitly requested.
- Prefer small, reviewable pull requests.
- Add or update documentation when behavior changes.
- Add tests or test cases for date calculation, OCR parsing, and reminder scheduling.

## Date calculation requirements

- Support two modes:

  - production date + shelf life -> calculated expiry date.
  - manual final edible date -> direct expiry date.
- Support shelf life units: day, month, year.
- If opened date and after-open shelf life exist, reminder date should use the earlier date between unopened expiry and after-open expiry.
- Handle invalid or incomplete dates gracefully.

## OCR requirements

- OCR flow: image -> raw text -> structured extraction -> confirmation form -> save.
- Return field-level confidence when possible.
- Preserve raw OCR text for debugging when user consents.
- Never auto-save low-confidence OCR results.

## Reminder requirements

- Default reminder offsets: 7, 3, 1, and 0 days before expiry.
- Respect user reminder switch.
- Subscription message authorization must be user-initiated.
- If subscription message is unavailable, keep in-app reminder lists working.

## Before opening a PR

- Explain what changed.
- List files modified.
- Provide manual test steps.
- Include screenshots for UI changes.
- Mention risks or edge cases.
