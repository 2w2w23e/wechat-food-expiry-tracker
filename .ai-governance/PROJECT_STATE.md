# Project State

Last updated: 2026-06-20

## Project

食期管家 / WeChat Food Expiry Tracker.

Repository: `2w2w23e/wechat-food-expiry-tracker`.

Type: Android APK local app first; WeChat Mini Program archived.

Active client: `apk/`.

Archived client: `miniprogram/`.

## Product Rules

- `expiryDate` is the canonical final edible date field.
- `expiryDate` is used for sorting, status, and future reminder design.
- `quantity` and `remainingQuantity` are separate fields.
- Sensitive keys must not be stored in Mini Program or Android client code, frontend code, or public files.
- Recognition or lookup results must be confirmed by the user before saving.

## Version State

- Android APK development is temporarily the active direction.
- WeChat Mini Program V0 core local workflow passed user manual acceptance and is archived under `miniprogram/`.
- The earlier V0.1 Mini Program food search plan is paused while the APK direction is active.

## Archived V0.1 Search Boundary

- Add home-list food search.
- Search at minimum by food name.
- Search may include simple local fields if it stays simple.
- Search must combine with existing status and category filters.
- Search results must still sort by `expiryDate`.
- Empty results should show a clear message.
- Do not add cloud, barcode, reminders, or new dependencies.

This boundary is retained for history only. Do not resume it unless the user explicitly reopens Mini Program development.

## Role System

Use the clean RepoMind OS role files going forward:

- PROJECT_ARCHITECT
- MAIN_BRAIN
- CODEX_TASK_GENERATOR
- CODE_REVIEWER
- DOCUMENTATION_ROLE
- LEARNING_COACH
- DEV_GUIDE

## Next Action

Continue APK development from `apk/`. Keep `miniprogram/` sealed unless the user explicitly reopens Mini Program work.
