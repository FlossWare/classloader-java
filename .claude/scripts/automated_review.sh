#!/bin/bash
# Single iteration of automated review workflow
# Called by Claude /loop every 10 minutes

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$PROJECT_ROOT"

echo "========================================="
echo "Automated Review Cycle - $(date)"
echo "========================================="

# Step 1: Run code review
echo "Step 1: Running code review scans..."
"$SCRIPT_DIR/code_review.sh"

# Step 2: Parse results and create issues
echo ""
echo "Step 2: Creating GitHub issues from findings..."
ISSUES_OUTPUT=$("$SCRIPT_DIR/create_review_issues.py" 2>&1)
echo "$ISSUES_OUTPUT"

# Extract number of new issues created
NEW_ISSUES=$(echo "$ISSUES_OUTPUT" | grep -oP "Created \K\d+" | head -1 || echo "0")

# Step 3: Check for uncommitted fixes and push them
echo ""
echo "Step 3: Checking for fixes to commit..."

# Stage any modified files (from auto-fixes)
if ! git diff --quiet; then
    echo "Found uncommitted changes, staging and pushing..."

    # Stage changes
    git add -A

    # Create commit message
    COMMIT_MSG="fix: automated code review fixes

Applied automated fixes from code review scan.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

    # Commit
    git commit -m "$COMMIT_MSG"

    # Push to main
    git push github main

    echo "✓ Changes committed and pushed to main"
else
    echo "No changes to commit"
fi

# Step 4: Report results
echo ""
echo "========================================="
echo "Review Cycle Complete"
echo "========================================="
echo "New issues created: $NEW_ISSUES"
echo "Stop condition: Will continue until no new issues found for 2 cycles"
echo ""

# Return the count for stop condition detection
exit 0
