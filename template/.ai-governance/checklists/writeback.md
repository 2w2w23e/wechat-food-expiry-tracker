# Writeback Checklist

Before writing durable repository state, check:

- Is writeback truly needed, or is `NO_WRITEBACK` enough?
- Is the information useful beyond the current chat window?
- Is there evidence or explicit user confirmation?
- Is this a user preference rather than a project fact?
- If it is a user preference, should it go under `user_preferences/`?
- Is a single-role preference being incorrectly written as a global preference?
- Is this only a temporary task instruction that should not be written back?
- Are secrets, tokens, credentials, private chat transcripts, and unnecessary
  personal information excluded?
- Is the writeback type correct?
- Is the target file correct?
- Does this require user approval?
- Could this pollute memory with one-time speculation?
- Could this pollute the decision log with an unapproved or temporary idea?
- Is the update small enough to preserve state without copying noise?
- Will future GPT windows understand what changed and why?
