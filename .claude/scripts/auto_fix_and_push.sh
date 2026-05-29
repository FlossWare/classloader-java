#!/bin/bash
# Automatically fix code review issues and push to GitHub

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "========================================="
echo "Auto-Fix and Push - $(date)"
echo "========================================="

cd "$PROJECT_ROOT"

# Check if there are any changes
if git diff --quiet && git diff --cached --quiet; then
    echo "No changes to commit"
    exit 0
fi

# Get the list of changed files
CHANGED_FILES=$(git diff --name-only --cached)
if [ -z "$CHANGED_FILES" ]; then
    CHANGED_FILES=$(git diff --name-only)
fi

echo "Changed files:"
echo "$CHANGED_FILES"
echo ""

# Create commit message
COMMIT_MSG="fix: auto-fix code review findings

$(echo "$CHANGED_FILES" | head -10)
$([ $(echo "$CHANGED_FILES" | wc -l) -gt 10 ] && echo "... and $(( $(echo "$CHANGED_FILES" | wc -l) - 10 )) more files")

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# Stage all changes
git add -A

# Commit
echo "Creating commit..."
git commit -m "$COMMIT_MSG"

# Push to main
echo "Pushing to GitHub..."
git push github main

echo ""
echo "========================================="
echo "Changes pushed successfully!"
echo "========================================="
