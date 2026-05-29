#!/bin/bash
# Main automation loop: Review -> Create Issues -> Auto-fix -> Push
# Runs until no new issues are found

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAX_ITERATIONS=100
ITERATION=0
NO_ISSUES_COUNT=0

echo "========================================="
echo "Starting Automated Review Loop"
echo "========================================="
echo "Stop condition: No new issues for 2 consecutive runs"
echo ""

while [ $ITERATION -lt $MAX_ITERATIONS ]; do
    ITERATION=$((ITERATION + 1))
    echo ""
    echo "========================================="
    echo "Iteration $ITERATION"
    echo "========================================="

    # Step 1: Run code review
    echo "Step 1: Running code review..."
    "$SCRIPT_DIR/code_review.sh"

    # Step 2: Create GitHub issues
    echo ""
    echo "Step 2: Creating GitHub issues..."
    ISSUES_OUTPUT=$("$SCRIPT_DIR/create_review_issues.py" 2>&1)
    echo "$ISSUES_OUTPUT"

    # Check if any new issues were created
    NEW_ISSUES=$(echo "$ISSUES_OUTPUT" | grep "Created [0-9]" | grep -oE "[0-9]+" | head -1 || echo "0")

    if [ "$NEW_ISSUES" -eq 0 ]; then
        NO_ISSUES_COUNT=$((NO_ISSUES_COUNT + 1))
        echo ""
        echo "No new issues created. Count: $NO_ISSUES_COUNT/2"

        if [ $NO_ISSUES_COUNT -ge 2 ]; then
            echo ""
            echo "========================================="
            echo "STOP CONDITION MET"
            echo "========================================="
            echo "No new issues found for 2 consecutive runs."
            echo "Automation loop completed successfully."
            exit 0
        fi
    else
        NO_ISSUES_COUNT=0
        echo ""
        echo "Created $NEW_ISSUES new issue(s)"

        # Step 3: Auto-commit and push (if there are changes)
        echo ""
        echo "Step 3: Checking for fixes to commit..."
        if ! git diff --quiet || ! git diff --cached --quiet; then
            "$SCRIPT_DIR/auto_fix_and_push.sh"
        else
            echo "No changes to commit"
        fi
    fi

    # Wait before next iteration (10 minutes as requested)
    if [ $ITERATION -lt $MAX_ITERATIONS ]; then
        echo ""
        echo "Waiting 10 minutes before next iteration..."
        sleep 600
    fi
done

echo ""
echo "========================================="
echo "Max iterations ($MAX_ITERATIONS) reached"
echo "========================================="
