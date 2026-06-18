# Legacy Window Imports

Purpose: track old AI windows whose useful context should be summarized and imported through RepoMind OS.

Rule: old windows return summary packets, not raw chat logs.

Import flow:

1. Paste `.ai-governance/prompts/legacy_context_export_prompt.md` into an old AI window.
2. The old window returns a Legacy Window Memory Packet.
3. Paste that packet into the current Project Governor / Project Architect window.
4. Classify the packet through `CONTEXT_IMPORT_PROTOCOL.md`.
5. Write only verified or user-confirmed durable items to the correct repository file.

Suggested old windows to process:

- Project Architect / 项目地基规划
- Main Brain / 项目主脑
- Codex Task Generator / Codex 任务生成器
- Code Reviewer / 代码审查
- Documentation Role / 文档AI
- Learning Coach / 教学
- Dev Guide / 开发指导

Target destinations:

- `PROJECT_STATE.md` for verified current facts.
- `decisions/YYYY-MM.md` for approved decisions.
- `memory/*.md` for reusable lessons.
- `memory/ANTI_PATTERNS.md` for recurring traps.
- `handoff/CURRENT.md` for current status.
- `roles/*.md` only for approved role behavior.

Do not import:

- raw chat logs;
- secrets or credentials;
- unnecessary personal information;
- unverified claims as truth;
- stale implementation claims without repository verification.
