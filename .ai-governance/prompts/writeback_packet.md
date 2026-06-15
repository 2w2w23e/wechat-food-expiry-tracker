# Writeback Packet

Use this packet when the Project Governor / Main Brain asks Codex or the user to
write durable updates into repository files.

## Target Files

- `<target file>`
- `<target file>`

User preference writebacks must follow `PREFERENCE_PROTOCOL.md`.

## Reason For Writeback

`<why this information needs to survive beyond the chat window>`

## Exact Update Scope

- `<section or entry to add/change>`
- `<section or entry to add/change>`

## Content To Add Or Change

```text
<proposed durable content>
```

## Forbidden Content

Do not include:

- secrets, tokens, API keys, passwords, or credentials;
- raw private chat transcripts;
- unnecessary personal information;
- unverified claims presented as truth;
- unrelated implementation details;
- content outside the target files.

## Validation

Run or perform:

- confirm only target files changed;
- confirm no forbidden content was added;
- confirm the update matches the approved writeback type;
- confirm the affected governance links still point to existing files.

## Commit Rule

`<commit allowed or not allowed>`

If commit is allowed, include the requested commit message.
If commit is not allowed, leave changes unstaged and report `git status --short`.
