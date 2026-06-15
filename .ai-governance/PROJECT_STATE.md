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

Current practical status: V0 core local workflow passed user manual acceptance on 2026-06-15.

The user also identified a missing experience feature: food search in the home list.

Current recommended state label:

`V0 core accepted; V0.1 food search enhancement approved before V1 planning.`

## V0 Acceptance Result

User-confirmed acceptance result on 2026-06-15:

- V0 manual acceptance checks passed.
- The app should not move directly into V1 yet.
- A V0.1 local search enhancement should be planned first.

## V0.1 Search Enhancement Scope

V0.1 should add local food search without crossing V1 or data-security boundaries.

Recommended scope:

- Add a home-list search entry for food records.
- Search at minimum by food name.
- Search may also include category display name, storage method, and notes if the implementation remains simple.
- Search results must still combine with existing status and category filters.
- Search results must still sort by `expiryDate`.
- Empty search results should show a clear elder-friendly message.
- No new dependency should be introduced unless explicitly approved.
- Do not introduce cloud database, cloud functions, account system, barcode, OCR, AI, reminders, or multi-device sync.

## Current Governance Migration State

Migration mode: layered migration.

What is approved:

- `.ai-governance/` becomes the highest governance layer.
- Existing `docs/*` AI collaboration files are retained as historical governance documents and import evidence.
- The first writeback targets were `PROJECT_INTAKE.md`, `PROJECT_STATE.md`, `handoff/CURRENT.md`, and `decisions/2026-06.md`.
- V0.1 food search enhancement should happen before V1 planning.

What is not yet approved:

- Full migration of all old role prompts into `.ai-governance/roles/`.
- Deprecation or deletion of old `docs/*` governance documents.
- V1 cloud development implementation.
- Barcode, OCR, AI, or reminder implementation.
- New dependencies, tools, or Skills.

## Current Stop Boundaries

- Do not write implementation code from the Project Governor window.
- Do not ask Codex to modify source files without a bounded approved task packet or prompt.
- Do not move into V1 until V0.1 search scope is handled or explicitly deferred by the user.
- Do not create new RepoMind OS role files before role migration is approved.
- Do not store secrets, raw private chat transcripts, or unnecessary personal information.

## Recommended Next Governance Action

Prepare the next task as V0.1 food search enhancement.

Recommended route:

1. Project Governor records the accepted direction.
2. Main Brain or Project Governor prepares a bounded V0.1 task packet.
3. Prompt Architect or codex-task-generator turns it into a Codex prompt or `/goal + require.txt`.
4. Codex implements only the approved local search scope.
5. Code review verifies that search combines with filters and preserves `expiryDate` sorting.
