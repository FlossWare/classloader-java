---
name: auto-resolve-loop
description: Continuously resolve GitHub issues until interrupted
---

# Auto-Resolve Loop Skill

Continuously monitor and resolve GitHub issues in an infinite loop.

## What This Skill Does

Enters a **continuous loop** that:
1. **Constantly monitors** open GitHub issues
2. **Picks highest priority** issue available
3. **Implements complete solution** with tests
4. **Validates quality** (tests, formatting, linting)
5. **Commits and pushes** immediately
6. **Closes issue** with detailed summary
7. **IMMEDIATELY moves to next issue** without asking
8. **Continues indefinitely** until interrupted or no issues remain

**This is the "work in your absence" mode.**

## Usage

```bash
# Start continuous loop (runs until interrupted)
/auto-resolve-loop

# Start loop with filters
/auto-resolve-loop priority=high
/auto-resolve-loop label=bug
```

## Arguments

- **priority** (optional): Filter by priority (high, medium, low)
- **label** (optional): Filter by GitHub label

## How to Stop

- **Interrupt Claude Code** (Ctrl+C in CLI, Stop button in UI)
- **Close the session** (safe to stop at any point)
- **Say "stop"** in chat (Claude will finish current issue then stop)

The loop will automatically stop when:
- No more issues match the filter
- Context limit is reached (then auto-summarize and can resume with "continue")

## Behavior

### Loop Iteration
```
LOOP FOREVER:
  1. Check open issues: gh issue list --state open
  2. If no issues match filter:
     - Log: "No matching issues found"
     - Sleep 5 minutes
     - GOTO step 1
  3. Pick highest priority issue
  4. Implement solution:
     - Read code to understand patterns
     - Implement fix/feature
     - Write comprehensive tests
  5. Validate quality:
     - Run tests (must pass)
     - Format code
     - Verify linting
  6. Commit and push immediately:
     - Detailed commit message
     - Push to remote
  7. Close issue with summary
  8. GOTO step 1 (no pause, no question)
```

### Quality Gates (Never Skipped)

Every commit MUST pass:
- ✅ All existing tests pass (100%)
- ✅ New tests for new functionality
- ✅ Code formatted
- ✅ Linting passing
- ✅ Zero regressions

**If any gate fails, Claude fixes it before committing.**

### Issue Monitoring

Every 3-5 completed issues, Claude checks for:
- New issues created
- Priority changes
- Label updates
- Obsolete issues to close

This allows the loop to adapt to changing priorities.

## Example Session

```
User: /auto-resolve-loop

Claude:
🔄 Entering auto-resolve loop mode...
Will continuously resolve issues until interrupted.

Press Ctrl+C to stop at any time.

─────────────────────────────────────────

Iteration 1: Checking open issues...
Found 12 open issues.

Resolving #342 (P1): Fix Swing compilation errors
✅ Fixed and pushed (commit: a1b2c3d)
✅ Issue closed

Iteration 2: Checking open issues...
Found 11 open issues.

Resolving #339 (P1): Add semantic version validation
✅ Fixed and pushed (commit: d4e5f6g)
✅ Issue closed

Iteration 3: Checking open issues...
Found 10 open issues.

Resolving #333 (P2): Convert to parameterized tests
✅ Fixed and pushed (commit: h7i8j9k)
✅ Issue closed

... [continues automatically] ...

Iteration 15: Checking open issues...
Found 0 open issues matching filter.
Waiting 5 minutes before re-checking...

Iteration 16: Checking open issues...
Found 1 new issue!

Resolving #350 (P0): Security vulnerability in auth
✅ Fixed and pushed (commit: x9y8z7w)
✅ Issue closed

... [continues indefinitely] ...

[User presses Ctrl+C]

🛑 Auto-resolve loop interrupted.

Session Summary:
- Issues resolved: 47
- Commits pushed: 47
- Time elapsed: 3 hours 12 minutes
- Tests: 412 passing (226 → 412, +186 new)
- Zero regressions
- All issues closed
```

## Differences from /auto-resolve

| Feature | /auto-resolve | /auto-resolve-loop |
|---------|---------------|-------------------|
| Mode | One-shot (N issues) | Continuous (∞) |
| Stops after | N issues | Never / Interrupted |
| Asks for next batch | Yes | No (automatic) |
| Monitors new issues | No | Yes (every 3-5 issues) |
| Best for | Quick fixes (5-20) | Large backlogs (50+) |
| Token usage | Fixed/Predictable | Unbounded |
| "Work in absence" | No | Yes |

## When to Use

**Use /auto-resolve-loop when:**
- ✅ You have a large backlog (50+ issues)
- ✅ You want continuous work while away (lunch, overnight)
- ✅ Your test suite is comprehensive (high confidence)
- ✅ Issues are well-defined with clear acceptance criteria
- ✅ You trust the autonomous workflow

**DON'T use /auto-resolve-loop when:**
- ❌ Your test coverage is <80%
- ❌ Issues are vague or require human judgment
- ❌ You want to review each fix before the next
- ❌ You're testing autonomous mode for the first time

Start with `/auto-resolve` (one-shot) first to build confidence, then graduate to `/auto-resolve-loop`.

## Safety Features

1. **Test-Gated Commits**: Nothing commits unless ALL tests pass
2. **Quality Gates**: Formatting and linting verified before every commit
3. **Immediate Push**: Fast feedback from CI on every commit
4. **Issue Summaries**: Detailed close comments for audit trail
5. **Context Monitoring**: Auto-summarizes when approaching limit
6. **Graceful Stop**: Finishes current issue before stopping

## Resuming After Context Limit

When context limit is reached:
```
Session Summary:
- Issues resolved: 23
- Token usage: 198K/200K (99%)
- Approaching context limit, will summarize...

[Session compacts and summarizes]

To resume auto-resolve loop, say: continue
```

Then just type:
```
continue
```

Claude resumes the loop where it left off.

## Prerequisites

Your project MUST have:
- ✅ Comprehensive test suite (80%+ coverage)
- ✅ Fast tests (<10 seconds total)
- ✅ Automated code formatting
- ✅ Linting/static analysis
- ✅ Well-defined GitHub issues
- ✅ GitHub CLI configured
- ✅ CI/CD pipeline (recommended)

## See Also

- `/auto-resolve` - One-shot issue resolution
- `/auto-review` - One-shot code review  
- `/auto-review-loop` - Continuous code review
- [AUTO_RESOLVE_MODE.md](../../AUTO_RESOLVE_MODE.md) - Full documentation
