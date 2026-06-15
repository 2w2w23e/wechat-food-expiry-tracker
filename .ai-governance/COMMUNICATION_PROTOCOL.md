# Communication Protocol

## Purpose

RepoMind OS supports multiple web GPT windows working on the same project.
Those windows coordinate through explicit packets and repository files.

## Communication Model

- GPT windows do not share memory.
- A role window must not assume it knows the context of another window.
- Shared state can only come from:
  - repository files;
  - user-pasted packets.
- Chat messages are temporary workspace, not durable project state.
- The repository is the durable coordination layer.

## Roles In Daily Communication

- Project Governor / Main Brain:
  - chooses the current purpose;
  - selects the minimum files another role should read;
  - prepares a Role Task Packet;
  - receives Role Result Packets;
  - decides whether a repository writeback should be proposed.
- Specialist role window:
  - reads only the requested files and pasted packet;
  - performs the assigned task within boundaries;
  - returns a Role Result Packet;
  - does not rely on unseen context from other windows;
  - reports global rules or cross-role preferences back in the Role Result
    Packet instead of persisting them directly.
- User:
  - copies packets between windows;
  - approves durable changes when approval is required.

## Role Task Packet

The Project Governor / Main Brain may prepare a Role Task Packet for another
role. The packet must include enough context for the target role to work without
assuming hidden chat history.

Use `prompts/role_task_packet.md`.

## Role Result Packet

Every role window must return a Role Result Packet to the user for delivery
back to the Project Governor / Main Brain.

Use `prompts/role_result_packet.md`.

## Writeback Responsibility

Specialist roles may recommend writeback, but they do not decide durable
project memory by themselves.

The Project Governor / Main Brain evaluates whether the result requires:

- no writeback;
- handoff update;
- memory update;
- decision log update;
- project state update;
- role update;
- context index update;
- anti-pattern update;
- user preference update.

Long-term writeback requires the proper protocol and user approval when the
change affects direction, decisions, role authority, or durable memory.

## Preference Handling

If a non-Project Governor / Main Brain role receives a global rule or cross-role
preference, it must not persist it directly. It should recommend a
`USER_PREFERENCE_UPDATE` in the Role Result Packet so the Project Governor /
Main Brain can classify it with `PREFERENCE_PROTOCOL.md`.

## Minimum Packet Rule

Do not paste entire chat history when a structured packet is enough.
Include only the current purpose, evidence, required files, boundaries,
uncertainty, and requested output.
