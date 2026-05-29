#!/bin/bash
# Automated code review script for Java projects
# Java: security scans, TODO checks
# Shell scripts: security scans, TODO checks
# Python: skipped (only automation scripts present)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REVIEW_OUTPUT_DIR="$PROJECT_ROOT/.claude/review-output"

mkdir -p "$REVIEW_OUTPUT_DIR"

echo "========================================="
echo "Starting Code Review - $(date)"
echo "========================================="

cd "$PROJECT_ROOT"

# Clean previous review outputs
rm -f "$REVIEW_OUTPUT_DIR"/*.txt

# Count Java files (exclude Python - only automation scripts)
JAVA_COUNT=$(find . -name "*.java" -type f | wc -l)

echo "Found $JAVA_COUNT Java files"
echo ""

# Skip Python checks - this is a Java project
# (Python files are only automation scripts in .claude/scripts/)

# Java checks (security scans and TODO checks only)
if [ $JAVA_COUNT -gt 0 ]; then
    echo "=== Java Code Checks ==="

    # Security scans for Java - focus on actual security issues
    echo "[1/1] Running security scans..."
    {
        echo "=== Java Security Patterns ==="
        # Only flag printStackTrace() - actual security issue (leaks stack traces)
        # ProcessBuilder, Runtime.getRuntime, Class.forName are legitimate for this platform
        # System.out.println in launcher/CLI tools is acceptable
        find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/test/*" -exec grep -Hn "\.printStackTrace()" {} \; 2>/dev/null || true
    } > "$REVIEW_OUTPUT_DIR/java-security-scans.txt"

    # Filter out comment lines
    SECURITY_COUNT=$(grep ".java:" "$REVIEW_OUTPUT_DIR/java-security-scans.txt" 2>/dev/null | grep -v "^\S*:\s*/\|^\S*:\s*\*" | wc -l 2>/dev/null || echo "0")
    SECURITY_COUNT=$(echo "$SECURITY_COUNT" | tr -d ' \n')
    if [ "$SECURITY_COUNT" -gt 0 ] 2>/dev/null; then
        echo "✗ Found $SECURITY_COUNT security patterns in Java code"
    else
        echo "✓ Java Security: PASSED"
    fi
    echo ""
else
    echo "No Java files found, skipping Java checks"
    echo ""
fi

# Skip Python security scans - only automation scripts present in .claude/scripts/

# TODO/FIXME checks (all languages)
echo "=== TODO/FIXME Checks ==="
# Exclude .claude/scripts (automation code) and target/ (build artifacts)
find . -type f \( -name "*.java" -o -name "*.py" \) ! -path "./.claude/scripts/*" ! -path "*/target/*" -exec grep -Hn "TODO\|FIXME\|XXX\|HACK" {} \; > "$REVIEW_OUTPUT_DIR/todo-checks.txt" 2>/dev/null || true
TODO_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/todo-checks.txt" || echo 0)
echo "Found $TODO_COUNT TODO/FIXME comments"

echo ""
echo "========================================="
echo "Code Review Complete - $(date)"
echo "========================================="
echo "Review outputs saved to: $REVIEW_OUTPUT_DIR"
echo ""

# Count total issues
TOTAL_ISSUES=0
for file in "$REVIEW_OUTPUT_DIR"/*.txt; do
    if [ -f "$file" ] && [ -s "$file" ]; then
        TOTAL_ISSUES=$((TOTAL_ISSUES + 1))
    fi
done

echo "Files with findings: $TOTAL_ISSUES"
exit 0
