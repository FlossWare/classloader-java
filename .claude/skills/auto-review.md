---
name: auto-review
description: Automatically review code for quality and security issues (one-shot)
---

# Auto-Review Skill

Automatically review the codebase for quality issues, security vulnerabilities, and improvement opportunities.

## What This Skill Does

Performs a comprehensive code review across multiple dimensions:

1. **Security Scan** - OWASP Top 10 vulnerabilities
2. **Code Quality** - Complexity, duplication, code smells  
3. **Test Coverage** - Gaps, missing tests, untested code
4. **Documentation** - Missing JavaDoc, TODO/FIXME audit
5. **Dependencies** - Outdated versions, known CVEs

For each finding:
- Verifies it's a real issue (no false positives)
- Assesses severity (P0/P1/P2/P3)
- Creates GitHub issues for P0/P1
- Auto-fixes P0 (critical) issues immediately
- Logs P2/P3 for future reference

## See Also

- `/auto-review-loop` - Continuous code review
- `/auto-resolve` - One-shot issue resolution
- `/auto-resolve-loop` - Continuous issue resolution
- [CONTINUOUS_REVIEW_GUIDE.md](../../CONTINUOUS_REVIEW_GUIDE.md) - Full documentation
