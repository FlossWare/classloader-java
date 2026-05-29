#!/bin/bash
# Main review loop - runs review, creates issues, and repeats
# Stops when no new issues are found for 2 consecutive iterations

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REVIEW_OUTPUT_DIR="$PROJECT_ROOT/.claude/review-output"

NO_ISSUES_COUNT=0
ITERATION=1

echo "╔════════════════════════════════════════╗"
echo "║  Automated Code Review Loop Starting   ║"
echo "╚════════════════════════════════════════╝"
echo ""

cd "$PROJECT_ROOT"

while true; do
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  Iteration #$ITERATION"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    # Step 1: Run code review
    echo "Step 1: Running code review..."
    "$SCRIPT_DIR/code_review.sh"

    # Check if any review outputs have content
    ISSUES_FOUND=false
    for file in "$REVIEW_OUTPUT_DIR"/*.txt; do
        if [ -f "$file" ] && [ -s "$file" ]; then
            ISSUES_FOUND=true
            break
        fi
    done

    if [ "$ISSUES_FOUND" = false ]; then
        NO_ISSUES_COUNT=$((NO_ISSUES_COUNT + 1))
        echo ""
        echo "✓ No issues found (count: $NO_ISSUES_COUNT/2)"

        if [ $NO_ISSUES_COUNT -ge 2 ]; then
            echo ""
            echo "╔════════════════════════════════════════╗"
            echo "║   Review Loop Complete - No Issues!    ║"
            echo "╚════════════════════════════════════════╝"
            echo ""
            echo "All code quality checks passed for 2 consecutive iterations."
            exit 0
        fi
    else
        NO_ISSUES_COUNT=0
        echo ""
        echo "Step 2: Creating remote issues..."
        python3 "$SCRIPT_DIR/create_review_issues.py"
    fi

    echo ""
    echo "Waiting 10 minutes before next iteration..."
    echo "(Press Ctrl+C to stop)"

    # Wait 10 minutes (600 seconds)
    sleep 600

    ITERATION=$((ITERATION + 1))
done
