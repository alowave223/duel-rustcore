---
name: config-schema-and-code-sync
description: Workflow command scaffold for config-schema-and-code-sync in duel-rustcore.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /config-schema-and-code-sync

Use this workflow when working on **config-schema-and-code-sync** in `duel-rustcore`.

## Goal

Extends or modifies configuration options and updates code to parse or use new config fields.

## Common Files

- `src/main/resources/config.yml`
- `src/main/java/net/rustcore/duel/arena/Arena.java`
- `src/main/java/net/rustcore/duel/lobby/LobbyManager.java`
- `src/main/resources/plugin.yml`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Edit src/main/resources/config.yml to add or change settings
- Update relevant Java classes to read/use the new config fields (e.g., Arena.java, LobbyManager.java)
- If needed, update plugin.yml or other resource files

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.