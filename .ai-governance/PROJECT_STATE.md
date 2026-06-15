# Project State

Last updated: 2026-06-15

## Governance Status

RepoMind OS is the highest governance layer for this repository, approved by the user on 2026-06-15 using layered migration.

Previous governance documents under `docs/` remain available as historical governance documents and import evidence. They are not automatically higher authority than `.ai-governance/`.

## Verified Project Identity

Project: 食期管家 / WeChat Food Expiry Tracker.

Repository: `2w2w23e/wechat-food-expiry-tracker`.

Type: WeChat Mini Program application.

Primary product goal: help household users manage food inventory and expiry dates with simple, elder-friendly flows.

## Verified Product Rules

- `expiryDate` is the canonical final edible date field.
- `expiryDate` is used for sorting, expiry status, and future reminder design.
- `quantity` and `remainingQuantity` are separate fields.
- Barcode, OCR, and AI recognition results must not be saved directly without user confirmation.
- API keys, OCR keys, OpenAI keys, product database keys, cloud credentials, or other secrets must not be stored in mini program frontend code or public repository files.
- Capabilities involving OCR, AI, barcode lookup, subscription messages, cloud functions, or database permissions must be handled with explicit security review before implementation.

## Verified Implementation Snapshot

As of the latest repository review after PR #7:

- The mini program has `pages/index/index` and `pages/food-detail/food-detail` registered in `app.json`.
- `utils/date.js` implements date-only expiry calculation and validation.
- `utils/food.js` implements food item normalization.
- `utils/foodStatus.js` implements expiry status classification.
- `utils/foodList.js` implements expiry-date sorting, status/category filtering helpers, and local list initialization helpers.
- `utils/category.js` implements standard food categories and Chinese/pinyin/initial search helpers.
- `mock/foods.js` contains 20 sample food records covering standard categories and status examples.
- `pages/index/index.js` implements the V0 local homepage flow, including local storage reads/writes, add-food form, status/category filtering, statistics, sample-data loading, and navigation to detail page.
- `pages/food-detail/food-detail.js` implements local detail view, edit flow, delete confirmation, and local storage updates.
- Tests exist for date utilities, food normalization/mock data, food status/list behavior, and category search.

## Current Product Version State

Current version: V0.

Current practical status: V0 local version appears largely implemented in code after PR #7, but project governance docs still need synchronization and user/manual verification before declaring V0 complete.

Current recommended state label:

`V0 local build: code-complete candidate, pending governance/documentation sync and manual acceptance review.`

## Known Documentation Drift

The following drift was observed during bootstrap review:

- `README.md` still describes the project as early foundation-building, while code already includes V0 local flows.
- `docs/PHASE_STATUS.md` still labels V0-6 as preparing to start, while PR #7 appears to include V0 local completion work.
- `docs/PHASE_STATUS.md` still contains older wording about detail display that may no longer match the independent `food-detail` page.
- `docs/VERSION_ROADMAP.md` states V0 is still being advanced and should be reviewed against the latest implementation.

## Current Governance Migration State

Migration mode: layered migration.

What is approved:

- `.ai-governance/` becomes the highest governance layer.
- Existing `docs/*` AI collaboration files are retained as historical governance documents and import evidence.
- The first writeback targets are `PROJECT_INTAKE.md`, `PROJECT_STATE.md`, `handoff/CURRENT.md`, and `decisions/2026-06.md`.

What is not yet approved:

- Full migration of all old role prompts into `.ai-governance/roles/`.
- Deprecation or deletion of old `docs/*` governance documents.
- V1 cloud development implementation.
- Barcode, OCR, AI, or reminder implementation.
- New dependencies, tools, or Skills.

## Current Stop Boundaries

- Do not write implementation code during governance bootstrap.
- Do not ask Codex to modify source files until governance handoff is clear.
- Do not move into V1 until V0 acceptance and documentation sync are explicitly handled.
- Do not create new RepoMind OS role files before role migration is approved.
- Do not store secrets, raw private chat transcripts, or unnecessary personal information.

## Recommended Next Governance Action

Synchronize the old foundation docs with the verified repository state and RepoMind OS hierarchy.

Suggested target documents for the next approved documentation sync:

- `README.md`
- `docs/PHASE_STATUS.md`
- `docs/VERSION_ROADMAP.md`
- possibly `docs/AI_COLLABORATION.md`
- possibly `docs/ROLE_PROMPTS.md`
- possibly `docs/DOCUMENT_OWNERSHIP.md`

Do not start implementation work until the user confirms the next documentation-sync scope.
