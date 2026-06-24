---
name: feature-implementation-with-manager-class
description: Workflow command scaffold for feature-implementation-with-manager-class in duel-rustcore.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /feature-implementation-with-manager-class

Use this workflow when working on **feature-implementation-with-manager-class** in `duel-rustcore`.

## Goal

Implements a new core feature by adding a new Manager class, then wiring it into existing systems and plugin lifecycle.

## Common Files

- `src/main/java/net/rustcore/duel/arena/*Manager.java`
- `src/main/java/net/rustcore/duel/arena/ArenaManager.java`
- `src/main/java/net/rustcore/duel/DuelsPlugin.java`
- `src/main/java/net/rustcore/duel/arena/ActiveArena.java`
- `src/main/java/net/rustcore/duel/arena/PlacedBlockTracker.java`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Add new Manager class in src/main/java/net/rustcore/duel/arena/
- Wire the Manager into ArenaManager and/or DuelsPlugin for initialization and integration
- Update or create related classes to use the new Manager (e.g., ActiveArena, PlacedBlockTracker)
- Update configuration if needed

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.