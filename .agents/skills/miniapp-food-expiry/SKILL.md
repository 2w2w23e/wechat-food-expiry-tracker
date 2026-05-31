
# Miniapp Food Expiry Workflow



## First steps



1. Identify the task type: product, UI, data model, date calculation, OCR, reminder, cloud function, test, or documentation.

2. Read `docs/PRD.md`, `docs/DATA_MODEL.md`, and relevant implementation files before changing code.

3. Prefer small changes that can be reviewed independently.



## Product invariants



- `expiryDate` is the canonical date used for sorting and reminders.

- Users can create `expiryDate` by calculation or manual input.

- OCR output must go through a confirmation screen.

- Secrets must not be placed in Mini Program client code.

- The app stores label and reminder information; it must not claim that food is medically or absolutely safe.



## Date calculation checklist



For any date calculation change, verify:



- production date + days

- production date + months

- production date + years

- leap year cases

- month-end cases

- manual expiry date mode

- invalid or missing input

- opened date + after-open shelf life



## OCR checklist



For OCR-related changes, verify:



- raw OCR text is preserved for confirmation/debugging when appropriate

- date formats are normalized

- shelf life units are normalized

- low-confidence fields are visibly editable

- user confirmation is required before saving



## Reminder checklist



For reminder-related changes, verify:



- default offsets are 7, 3, 1, 0

- reminders are not scheduled when disabled

- expired items display correctly

- subscription-message authorization is respected

- in-app reminder fallback remains available



## Output expectations



When implementing code:



- Summarize files changed.

- Explain behavior changes.

- Provide manual test steps.

- Mention edge cases not covered.
