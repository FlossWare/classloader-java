---
name: auto-resolve
description: Automatically resolve GitHub issues (one-shot mode)
---

# Auto-Resolve Skill

Automatically resolve the highest priority GitHub issues with complete solutions.

## What This Skill Does

1. **Checks open GitHub issues** using `gh issue list`
2. **Picks the highest priority** issue (P0 > P1 > P2 > P3, or by age)
3. **Implements a complete solution**:
   - Reads relevant code to understand patterns
   - Implements the fix/feature
   - Writes comprehensive tests (unit + integration)
4. **Validates quality**:
   - Runs all tests (must pass)
   - Formats code (mvn spotless:apply or equivalent)
   - Verifies linting (checkstyle, eslint, etc.)
5. **Commits and pushes immediately**:
   - Detailed commit message explaining the change
   - Co-authored by Claude
6. **Closes the issue** with detailed summary
7. **Repeats for next N issues** (configurable, default 5)

## Usage

```bash
# Resolve next 5 issues (default)
/auto-resolve

# Resolve next 10 issues
/auto-resolve 10

# Resolve only P0/P1 issues
/auto-resolve priority=high

# Resolve issues with specific label
/auto-resolve label=bug
```

## Arguments

- **Number** (optional, default: 5): How many issues to resolve
- **priority** (optional): Filter by priority (high = P0/P1, medium = P2, low = P3)
- **label** (optional): Filter by GitHub label

## Quality Requirements

Every issue resolution MUST:
- ✅ Have passing tests (100% of existing tests must pass)
- ✅ Include new tests for new functionality
- ✅ Be formatted according to project style
- ✅ Pass all linting checks
- ✅ Have zero regressions
- ✅ Include detailed commit message
- ✅ Update documentation if API changed

## Example Session

```
User: /auto-resolve 3

Claude:
Checking open GitHub issues...
Found 12 open issues.

Resolving #342 (P1): Fix Swing compilation errors
├─ Reading platform-swing-ui module
├─ Fixing 3 AWT API mismatches
├─ Running tests... ✅ 226/226 passing
├─ Formatting code... ✅ Done
├─ Committing... ✅ a1b2c3d
├─ Pushing... ✅ Pushed to main
└─ Closing issue... ✅ Closed with summary

Resolving #339 (P1): Add semantic version validation
├─ Creating SemanticVersion class
├─ Updating ServiceRegistry interface
├─ Writing 20 new unit tests
├─ Running tests... ✅ 246/246 passing
├─ Formatting code... ✅ Done
├─ Committing... ✅ d4e5f6g
├─ Pushing... ✅ Pushed to main
└─ Closing issue... ✅ Closed with summary

Resolving #333 (P2): Convert to parameterized tests
├─ Converting RestartPolicyParserTest
├─ Converting HealthCheckConfigParserTest
├─ Reduced 150 lines of duplicated test code
├─ Running tests... ✅ 246/246 passing
├─ Formatting code... ✅ Done
├─ Committing... ✅ h7i8j9k
├─ Pushing... ✅ Pushed to main
└─ Closing issue... ✅ Closed with summary

✅ Auto-resolve complete:
- Issues resolved: 3
- Commits pushed: 3
- Tests: 246 passing (226 → 246, +20 new)
- Zero regressions
- All issues closed
```

## Differences from /auto-resolve-loop

| Feature | /auto-resolve | /auto-resolve-loop |
|---------|---------------|-------------------|
| Mode | One-shot | Continuous |
| Stops after | N issues | Never (until interrupted) |
| Best for | Quick fixes | Maintenance sprints |
| Token usage | Fixed (N issues) | Unbounded |
| User control | High | Low |

## When to Use

**Use /auto-resolve when:**
- You have 5-20 issues to resolve
- You want to review progress between batches
- You want predictable token usage
- You're testing the autonomous workflow

**Use /auto-resolve-loop when:**
- You have a large backlog (50+ issues)
- You want continuous work while you're away
- You trust the quality gates completely
- You have a comprehensive test suite

## Prerequisites

Your project MUST have:
- ✅ Comprehensive test suite
- ✅ Automated code formatting
- ✅ Linting/static analysis
- ✅ Well-defined GitHub issues
- ✅ GitHub CLI configured (`gh auth login`)

## See Also

- `/auto-resolve-loop` - Continuous issue resolution
- `/auto-review` - One-shot code review
- `/auto-review-loop` - Continuous code review
- [AUTO_RESOLVE_MODE.md](../../AUTO_RESOLVE_MODE.md) - Full documentation
