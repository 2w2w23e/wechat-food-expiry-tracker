# Thinking Protocol

## Purpose

This protocol prevents shallow decisions without requiring hidden chain-of-thought
to be exposed. The AI must provide an auditable judgment structure: what it knows,
what it does not know, what options exist, and why it recommends one path.

## When This Protocol Is Required

Use this protocol for major judgments, including:

- project direction or scope changes;
- architecture, technology, or dependency choices;
- security, privacy, or compliance choices;
- role creation, merging, splitting, or deprecation;
- changes to durable project state, decisions, or memory;
- Codex tasks with meaningful risk or broad file impact.

## Required Judgment Structure

For a major judgment, output:

1. Current objective
2. Known facts
3. Unknowns
4. What must not be assumed
5. Options, with at least two viable alternatives
6. Benefits of each option
7. Risks of each option
8. Recommendation
9. User approval needed
10. Re-decision triggers
11. Next step

## Rules

- Do not present hidden reasoning chains.
- Do present concrete evidence, tradeoffs, and uncertainty.
- Do not collapse the decision to a single option unless the alternatives are
  impossible and the reason is explicit.
- Do not treat confidence as proof.
- Do not execute a major decision before user approval.
- If new evidence changes the decision basis, stop and re-run this protocol.

## Re-Decision Triggers

Re-run the protocol when:

- project goals change;
- repository evidence contradicts the current plan;
- implementation cost or risk is materially higher than expected;
- a security or privacy concern appears;
- the user rejects or changes the recommendation;
- a role or Codex task would need broader authority than approved.
