# Agent instructions

Resolve shared policy using `.team-policy/pointer.json` (paths relative to this repository). Clone the configured remote into the first candidate if missing; otherwise fetch and fast-forward the tracked branch when safe. Report blocked updates.

Before implementing, read the resolved checkout's `README.md` and follow its linked guidance relevant to the task.

For domain terminology, read `CONTEXT.md`.

When changing currencies or cross-plugin commands, read `docs/integrations.md`.

When working on the NPC Screens (`/sneakynpcs opengui3`; opengui2 is the earlier version and stays as it is), read `docs/HANDOFF.md` and check its status date against recent commits.
