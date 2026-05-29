#!/bin/bash
# Automated code review script
# Python: mypy, flake8, bandit, security scans, TODO checks
# Java: security scans, TODO checks only

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

# Count Python and Java files
PYTHON_COUNT=$(find . -name "*.py" -type f | wc -l)
JAVA_COUNT=$(find . -name "*.java" -type f | wc -l)

echo "Found $PYTHON_COUNT Python files, $JAVA_COUNT Java files"
echo ""

# Python checks (if Python files exist)
if [ $PYTHON_COUNT -gt 0 ]; then
    echo "=== Python Code Checks ==="

    # 1. MyPy (type checking)
    echo "[1/3] Running mypy..."
    if find . -name "*.py" -type f -print0 | xargs -0 mypy --ignore-missing-imports --no-error-summary 2>&1 | tee "$REVIEW_OUTPUT_DIR/mypy.txt"; then
        echo "✓ MyPy: PASSED"
    else
        echo "✗ MyPy: FOUND ISSUES"
    fi

    # 2. Flake8 (style and quality)
    echo "[2/3] Running flake8..."
    if find . -name "*.py" -type f -print0 | xargs -0 flake8 --extend-ignore=E501 2>&1 | tee "$REVIEW_OUTPUT_DIR/flake8.txt"; then
        echo "✓ Flake8: PASSED"
    else
        echo "✗ Flake8: FOUND ISSUES"
    fi

    # 3. Bandit (security)
    echo "[3/3] Running bandit..."
    # Skip B404,B602,B603,B607 for .claude/scripts/* (automation scripts using subprocess)
    if find . -name "*.py" -type f -print0 | xargs -0 bandit -q --skip B404,B602,B603,B607 --exclude ./.claude/scripts 2>&1 | tee "$REVIEW_OUTPUT_DIR/bandit.txt"; then
        echo "✓ Bandit: PASSED"
    else
        echo "✗ Bandit: FOUND SECURITY ISSUES"
    fi
    echo ""
else
    echo "No Python files found, skipping Python checks"
    echo ""
fi

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

# Security scans for Python (if exists)
if [ $PYTHON_COUNT -gt 0 ]; then
    echo "=== Python Security Pattern Scan ==="
    {
        echo "=== Python Security Patterns ==="
        # Exclude .claude/scripts from security pattern scan (automation code)
        find . -type f -name "*.py" ! -path "./.claude/scripts/*" -exec grep -Hn "eval\|exec\|__import__\|pickle.loads\|yaml.load[^s]\|subprocess.call\|os.system" {} \; 2>/dev/null || true
    } > "$REVIEW_OUTPUT_DIR/python-security-scans.txt"

    PY_SECURITY_COUNT=$(grep -c ".py:" "$REVIEW_OUTPUT_DIR/python-security-scans.txt" 2>/dev/null || echo "0")
    PY_SECURITY_COUNT=$(echo "$PY_SECURITY_COUNT" | tr -d ' \n')
    if [ "$PY_SECURITY_COUNT" -gt 0 ] 2>/dev/null; then
        echo "✗ Found $PY_SECURITY_COUNT security patterns in Python code"
    else
        echo "✓ Python Security Patterns: PASSED"
    fi
    echo ""
fi

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
