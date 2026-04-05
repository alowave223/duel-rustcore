---
name: duel-rustcore-conventions
description: Development conventions and patterns for duel-rustcore. Java project with mixed commits.
---

# Duel Rustcore Conventions

> Generated from [alowave223/duel-rustcore](https://github.com/alowave223/duel-rustcore) on 2026-03-24

## Overview

This skill teaches Claude the development patterns and conventions used in duel-rustcore.

## Tech Stack

- **Primary Language**: Java
- **Architecture**: hybrid module organization
- **Test Location**: separate

## When to Use This Skill

Activate this skill when:
- Making changes to this repository
- Adding new features following established patterns
- Writing tests that match project conventions
- Creating commits with proper message format

## Commit Conventions

Follow these commit message conventions based on 12 analyzed commits.

### Commit Style: Mixed Style

### Prefixes Used

- `feat`
- `config`

### Message Guidelines

- Average message length: ~74 characters
- Keep first line concise and descriptive
- Use imperative mood ("Add feature" not "Added feature")


*Commit message example*

```text
feat: wire SlimeArenaManager into allocation/deallocation, add block-revert for inter-round reset
```

*Commit message example*

```text
config: add polygon boundary and ASWM template-world settings to arena config
```

*Commit message example*

```text
feat: load polygon config per arena and attach shifted polygon to ActiveArena instances
```

*Commit message example*

```text
feat: initialize SlimeArenaManager on plugin enable with fallback guard
```

*Commit message example*

```text
feat: add SlimeArenaManager for per-duel world cloning via ASWM
```

*Commit message example*

```text
feat: add CustomPoly2D polygon geometry with ray-casting contains() and YAML serialization
```

*Commit message example*

```text
config: add ASWM dependency and softdepend for SlimeWorldManager integration
```

*Commit message example*

```text
feat: replace hardcoded action switch with generic execute-on-use dispatch
```

## Architecture

### Project Structure: Single Package

This project uses **hybrid** module organization.

### Source Layout

```
src/
├── main/
```

### Guidelines

- This project uses a hybrid organization
- Follow existing patterns when adding new code

## Code Style

### Language: Java

### Naming Conventions

| Element | Convention |
|---------|------------|
| Files | PascalCase |
| Functions | camelCase |
| Classes | PascalCase |
| Constants | SCREAMING_SNAKE_CASE |

### Import Style: Relative Imports

### Export Style: Named Exports


*Preferred import style*

```typescript
// Use relative imports
import { Button } from '../components/Button'
import { useAuth } from './hooks/useAuth'
```

*Preferred export style*

```typescript
// Use named exports
export function calculateTotal() { ... }
export const TAX_RATE = 0.1
export interface Order { ... }
```

## Common Workflows

These workflows were detected from analyzing commit patterns.

### Feature Development

Standard feature implementation workflow

**Frequency**: ~25 times per month

**Steps**:
1. Add feature implementation
2. Add tests for feature
3. Update documentation

**Example commit sequence**:
```
feat: implement item-flags configuration for lobby items and enhance duel mechanics
config: replace hardcoded action strings with execute-on-use command lists
feat: parse execute-on-use commands in LobbyManager, replace action string with action list
```

### Feature Implementation With Manager Class

Implements a new core feature by adding a new Manager class, then wiring it into existing systems and plugin lifecycle.

**Frequency**: ~2 times per month

**Steps**:
1. Add new Manager class in src/main/java/net/rustcore/duel/arena/
2. Wire the Manager into ArenaManager and/or DuelsPlugin for initialization and integration
3. Update or create related classes to use the new Manager (e.g., ActiveArena, PlacedBlockTracker)
4. Update configuration if needed

**Files typically involved**:
- `src/main/java/net/rustcore/duel/arena/*Manager.java`
- `src/main/java/net/rustcore/duel/arena/ArenaManager.java`
- `src/main/java/net/rustcore/duel/DuelsPlugin.java`
- `src/main/java/net/rustcore/duel/arena/ActiveArena.java`
- `src/main/java/net/rustcore/duel/arena/PlacedBlockTracker.java`

**Example commit sequence**:
```
Add new Manager class in src/main/java/net/rustcore/duel/arena/
Wire the Manager into ArenaManager and/or DuelsPlugin for initialization and integration
Update or create related classes to use the new Manager (e.g., ActiveArena, PlacedBlockTracker)
Update configuration if needed
```

### Config Schema And Code Sync

Extends or modifies configuration options and updates code to parse or use new config fields.

**Frequency**: ~2 times per month

**Steps**:
1. Edit src/main/resources/config.yml to add or change settings
2. Update relevant Java classes to read/use the new config fields (e.g., Arena.java, LobbyManager.java)
3. If needed, update plugin.yml or other resource files

**Files typically involved**:
- `src/main/resources/config.yml`
- `src/main/java/net/rustcore/duel/arena/Arena.java`
- `src/main/java/net/rustcore/duel/lobby/LobbyManager.java`
- `src/main/resources/plugin.yml`

**Example commit sequence**:
```
Edit src/main/resources/config.yml to add or change settings
Update relevant Java classes to read/use the new config fields (e.g., Arena.java, LobbyManager.java)
If needed, update plugin.yml or other resource files
```

### Feature Implementation With Config And Docs

Implements a new feature that requires both code and config changes, and documents the feature.

**Frequency**: ~2 times per month

**Steps**:
1. Implement feature in Java code (e.g., LobbyManager, Duel.java, Listeners)
2. Update src/main/resources/config.yml to support the feature
3. Add or update documentation files (e.g., docs/superpowers/plans/*.md, CLAUDE.md)

**Files typically involved**:
- `src/main/java/net/rustcore/duel/lobby/LobbyManager.java`
- `src/main/java/net/rustcore/duel/listener/LobbyListener.java`
- `src/main/java/net/rustcore/duel/listener/DuelListener.java`
- `src/main/java/net/rustcore/duel/duel/Duel.java`
- `src/main/resources/config.yml`
- `docs/superpowers/plans/*.md`
- `CLAUDE.md`

**Example commit sequence**:
```
Implement feature in Java code (e.g., LobbyManager, Duel.java, Listeners)
Update src/main/resources/config.yml to support the feature
Add or update documentation files (e.g., docs/superpowers/plans/*.md, CLAUDE.md)
```


## Best Practices

Based on analysis of the codebase, follow these practices:

### Do

- Use PascalCase for file names
- Prefer named exports

### Don't

- Don't deviate from established patterns without discussion

---

*This skill was auto-generated by [ECC Tools](https://ecc.tools). Review and customize as needed for your team.*
