#!/bin/bash
# BRUTAL Code Review - No Mercy for Bad Code
# Finds: complexity, missing docs, anti-patterns, design issues, potential bugs

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REVIEW_OUTPUT_DIR="$PROJECT_ROOT/.claude/review-output"

mkdir -p "$REVIEW_OUTPUT_DIR"

echo "========================================="
echo "BRUTAL Code Review - $(date)"
echo "No mercy for bad code!"
echo "========================================="

cd "$PROJECT_ROOT"

# Clean previous outputs
rm -f "$REVIEW_OUTPUT_DIR"/brutal-*.txt

JAVA_COUNT=$(find src/main/java -name "*.java" -type f 2>/dev/null | wc -l)
echo "Reviewing $JAVA_COUNT Java source files"
echo ""

# 1. MISSING JAVADOC
echo "[1/10] Hunting for missing JavaDoc..."
{
    echo "=== Public API without JavaDoc ==="
    find src/main/java -name "*.java" -type f -exec awk '
        /^[[:space:]]*(public|protected)[[:space:]]+(static[[:space:]]+)?(class|interface|enum|@interface|abstract[[:space:]]+class)/ {
            if (prev !~ /\/\*\*/) print FILENAME":"NR":"$0
        }
        /^[[:space:]]*(public|protected)[[:space:]]+[^{;]*\(/ {
            if (prev !~ /\/\*\*/ && $0 !~ /@Override/) print FILENAME":"NR":"$0
        }
        { prev = $0 }
    ' {} \;
} > "$REVIEW_OUTPUT_DIR/brutal-missing-javadoc.txt" 2>/dev/null || true

JAVADOC_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-missing-javadoc.txt" 2>/dev/null || echo "0")
if [ "$JAVADOC_COUNT" -gt 0 ]; then
    echo "✗ Found $JAVADOC_COUNT public APIs without JavaDoc"
else
    echo "✓ All public APIs documented"
fi

# 2. EXCEPTION SWALLOWING
echo "[2/10] Looking for swallowed exceptions..."
{
    echo "=== Empty Catch Blocks (Exception Swallowing) ==="
    find src/main/java -name "*.java" -exec grep -Pzo '(?s)catch\s*\([^)]+\)\s*\{\s*\}' {} \; 2>/dev/null | grep -v "^Binary" || true
    find src/main/java -name "*.java" -exec grep -A2 "catch.*Exception" {} \; | grep -B1 "^\s*//.*ignore\|^\s*//.*empty" || true
} > "$REVIEW_OUTPUT_DIR/brutal-swallowed-exceptions.txt" 2>/dev/null || true

SWALLOW_COUNT=$(grep -c "catch" "$REVIEW_OUTPUT_DIR/brutal-swallowed-exceptions.txt" 2>/dev/null || echo "0")
if [ "$SWALLOW_COUNT" -gt 0 ]; then
    echo "✗ Found $SWALLOW_COUNT potential swallowed exceptions"
else
    echo "✓ No obvious exception swallowing"
fi

# 3. MAGIC NUMBERS
echo "[3/10] Finding magic numbers..."
{
    echo "=== Magic Numbers (Non-constant literals) ==="
    find src/main/java -name "*.java" -exec grep -Hn '\b[0-9]{2,}\b' {} \; | grep -v "private static final\|public static final\|@\|//\|/\*" || true
} > "$REVIEW_OUTPUT_DIR/brutal-magic-numbers.txt" 2>/dev/null || true

MAGIC_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-magic-numbers.txt" 2>/dev/null || echo "0")
if [ "$MAGIC_COUNT" -gt 0 ]; then
    echo "✗ Found $MAGIC_COUNT potential magic numbers"
else
    echo "✓ No magic numbers found"
fi

# 4. GOD CLASSES (large files)
echo "[4/10] Detecting God Classes..."
{
    echo "=== God Classes (>500 lines) ==="
    find src/main/java -name "*.java" -type f -exec wc -l {} \; | awk '$1 > 500 {print}' | sort -rn
} > "$REVIEW_OUTPUT_DIR/brutal-god-classes.txt" 2>/dev/null || true

GOD_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-god-classes.txt" 2>/dev/null || echo "0")
if [ "$GOD_COUNT" -gt 0 ]; then
    echo "✗ Found $GOD_COUNT God Classes (>500 lines)"
else
    echo "✓ No God Classes found"
fi

# 5. DEEP NESTING
echo "[5/10] Checking for deep nesting..."
{
    echo "=== Deep Nesting (>3 levels) ==="
    find src/main/java -name "*.java" -exec awk '
        {
            indent = match($0, /[^ \t]/);
            if (indent > 0 && (indent-1)/4 > 3 && $0 ~ /if|for|while|switch/)
                print FILENAME":"NR":"(indent-1)/4" levels:"$0
        }
    ' {} \;
} > "$REVIEW_OUTPUT_DIR/brutal-deep-nesting.txt" 2>/dev/null || true

NEST_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-deep-nesting.txt" 2>/dev/null || echo "0")
if [ "$NEST_COUNT" -gt 0 ]; then
    echo "✗ Found $NEST_COUNT deeply nested statements"
else
    echo "✓ No excessive nesting"
fi

# 6. NULL CHECKS WITHOUT VALIDATION
echo "[6/10] Finding missing null validation..."
{
    echo "=== Methods without null checks ==="
    find src/main/java -name "*.java" -exec awk '
        /^[[:space:]]*(public|protected|private)[[:space:]]+.*\([^)]*[A-Z][a-zA-Z]*[^)]*\)/ {
            if (!/@Nullable|Objects\.requireNonNull|if.*null|throw.*NullPointer/) {
                print FILENAME":"NR":"$0
            }
        }
    ' {} \; | head -50
} > "$REVIEW_OUTPUT_DIR/brutal-missing-null-checks.txt" 2>/dev/null || true

NULL_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-missing-null-checks.txt" 2>/dev/null || echo "0")
if [ "$NULL_COUNT" -gt 0 ]; then
    echo "✗ Found $NULL_COUNT methods potentially missing null checks"
else
    echo "✓ Null checking looks good"
fi

# 7. MUTABLE STATIC FIELDS
echo "[7/10] Hunting for mutable static state..."
{
    echo "=== Mutable Static Fields (not final) ==="
    find src/main/java -name "*.java" -exec grep -Hn "private static [^f].*=\|public static [^f].*=" {} \; | grep -v "final\|Logger" || true
} > "$REVIEW_OUTPUT_DIR/brutal-mutable-static.txt" 2>/dev/null || true

MUTABLE_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-mutable-static.txt" 2>/dev/null || echo "0")
if [ "$MUTABLE_COUNT" -gt 0 ]; then
    echo "✗ Found $MUTABLE_COUNT mutable static fields"
else
    echo "✓ No mutable static state"
fi

# 8. RESOURCE LEAKS
echo "[8/10] Checking for resource leaks..."
{
    echo "=== Potential Resource Leaks (no try-with-resources) ==="
    find src/main/java -name "*.java" -exec grep -Hn "new.*Stream\|new.*Reader\|new.*Writer\|new.*Connection" {} \; | grep -v "try (" || true
} > "$REVIEW_OUTPUT_DIR/brutal-resource-leaks.txt" 2>/dev/null || true

LEAK_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-resource-leaks.txt" 2>/dev/null || echo "0")
if [ "$LEAK_COUNT" -gt 0 ]; then
    echo "✗ Found $LEAK_COUNT potential resource leaks"
else
    echo "✓ Resources properly managed"
fi

# 9. OVERLY BROAD EXCEPTION CATCHING
echo "[9/10] Finding overly broad exception handling..."
{
    echo "=== Catching Generic Exceptions ==="
    find src/main/java -name "*.java" -exec grep -Hn "catch.*Exception\s*e)\|catch.*Throwable" {} \; | grep -v "IOException\|SQLException\|InterruptedException" || true
} > "$REVIEW_OUTPUT_DIR/brutal-broad-exceptions.txt" 2>/dev/null || true

BROAD_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-broad-exceptions.txt" 2>/dev/null || echo "0")
if [ "$BROAD_COUNT" -gt 0 ]; then
    echo "✗ Found $BROAD_COUNT overly broad exception catches"
else
    echo "✓ Exception handling is specific"
fi

# 10. MISSING @Override
echo "[10/10] Checking for missing @Override annotations..."
{
    echo "=== Missing @Override Annotations ==="
    find src/main/java -name "*.java" -exec awk '
        /@Override/ { override=1; next }
        /^[[:space:]]*(public|protected)[[:space:]]+.*\(/ {
            if (!override && ($0 ~ /equals\(|hashCode\(|toString\(|compareTo\(/))
                print FILENAME":"NR":"$0
            override=0
        }
        { if ($0 !~ /^[[:space:]]*$/ && $0 !~ /^[[:space:]]*\//) override=0 }
    ' {} \;
} > "$REVIEW_OUTPUT_DIR/brutal-missing-override.txt" 2>/dev/null || true

OVERRIDE_COUNT=$(wc -l < "$REVIEW_OUTPUT_DIR/brutal-missing-override.txt" 2>/dev/null || echo "0")
if [ "$OVERRIDE_COUNT" -gt 0 ]; then
    echo "✗ Found $OVERRIDE_COUNT missing @Override annotations"
else
    echo "✓ @Override annotations look good"
fi

echo ""
echo "========================================="
echo "BRUTAL Review Complete - $(date)"
echo "========================================="

# Count total findings
TOTAL_FINDINGS=0
for file in "$REVIEW_OUTPUT_DIR"/brutal-*.txt; do
    if [ -f "$file" ] && [ -s "$file" ]; then
        COUNT=$(wc -l < "$file")
        if [ "$COUNT" -gt 0 ]; then
            TOTAL_FINDINGS=$((TOTAL_FINDINGS + COUNT))
        fi
    fi
done

echo "Total findings: $TOTAL_FINDINGS"
echo ""

if [ $TOTAL_FINDINGS -eq 0 ]; then
    echo "🎉 Code passed brutal review!"
else
    echo "💀 Fix these issues or face the consequences!"
fi

exit 0
