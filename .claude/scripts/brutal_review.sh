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
    # Track whether we are inside a JavaDoc block (/** ... */) and whether the
    # most recently closed comment block was JavaDoc.  When we encounter a public
    # or protected declaration, we check whether there was a JavaDoc comment that
    # ended between the declaration and the preceding code -- allowing for
    # annotations (including @Override), blank lines, or single-line /** ... */
    # comments in between.
    find src/main/java -name "*.java" -type f -exec awk '
        BEGIN { in_javadoc = 0; has_javadoc = 0; has_override = 0 }

        # Start of a JavaDoc comment block
        /\/\*\*/ {
            in_javadoc = 1
            has_javadoc = 1
        }

        # End of any block comment -- if we were in a JavaDoc block, mark it
        /\*\// {
            if (in_javadoc) {
                in_javadoc = 0
                # has_javadoc remains 1 until consumed or reset
            }
        }

        # Skip lines inside JavaDoc/block comments (lines starting with *)
        /^[[:space:]]*\*/ { next }

        # Track @Override annotation on its own line
        /^[[:space:]]*@Override[[:space:]]*$/ {
            has_override = 1
            next
        }

        # Other annotations and blank lines between JavaDoc and declaration are
        # OK -- do not reset has_javadoc or has_override for those.
        /^[[:space:]]*@/ { next }
        /^[[:space:]]*$/ { next }

        # Single-line comments should not reset state either
        /^[[:space:]]*\/\// { next }

        # Check class/interface/enum declarations
        /^[[:space:]]*(public|protected)[[:space:]]+(static[[:space:]]+)?(class|interface|enum|@interface|abstract[[:space:]]+class)/ {
            if (!has_javadoc) print FILENAME":"NR":"$0
            has_javadoc = 0
            has_override = 0
            next
        }

        # Check method/constructor declarations (lines containing a parenthesis)
        /^[[:space:]]*(public|protected)[[:space:]]+[^{;]*\(/ {
            if (!has_javadoc && !has_override) print FILENAME":"NR":"$0
            has_javadoc = 0
            has_override = 0
            next
        }

        # Reset state on any other non-skipped line (code lines).  This prevents
        # a JavaDoc on one member from being attributed to a later member.
        {
            if (!in_javadoc) {
                has_javadoc = 0
                has_override = 0
            }
        }
    ' {} \;
} > "$REVIEW_OUTPUT_DIR/brutal-missing-javadoc.txt" 2>/dev/null || true

JAVADOC_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-missing-javadoc.txt" 2>/dev/null | grep -c . || true)
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

SWALLOW_COUNT=$(grep -c "catch" "$REVIEW_OUTPUT_DIR/brutal-swallowed-exceptions.txt" 2>/dev/null || true)
if [ "$SWALLOW_COUNT" -gt 0 ]; then
    echo "✗ Found $SWALLOW_COUNT potential swallowed exceptions"
else
    echo "✓ No obvious exception swallowing"
fi

# 3. MAGIC NUMBERS
echo "[3/10] Finding magic numbers..."
{
    echo "=== Magic Numbers (Non-constant literals) ==="
    find src/main/java -name "*.java" -exec grep -Hn '\b[0-9]{2,}\b' {} \; | grep -v "private static final\|public static final\|@\|//\|/\*\|^[^:]*:[^:]*:[[:space:]]*\*" || true
} > "$REVIEW_OUTPUT_DIR/brutal-magic-numbers.txt" 2>/dev/null || true

MAGIC_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-magic-numbers.txt" 2>/dev/null | grep -c . || true)
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

GOD_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-god-classes.txt" 2>/dev/null | grep -c . || true)
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

NEST_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-deep-nesting.txt" 2>/dev/null | grep -c . || true)
if [ "$NEST_COUNT" -gt 0 ]; then
    echo "✗ Found $NEST_COUNT deeply nested statements"
else
    echo "✓ No excessive nesting"
fi

# 6. NULL CHECKS WITHOUT VALIDATION
echo "[6/10] Finding missing null validation..."
{
    echo "=== Methods without null checks ==="
    # Look for public/protected methods with object parameters that genuinely lack null validation
    # Only flag methods that don't have Objects.requireNonNull in their first few lines
    find src/main/java -name "*.java" -exec awk '
        BEGIN { in_method=0; method_line=0 }
        /^[[:space:]]*(public|protected)[[:space:]]+(static|abstract|synchronized)?[[:space:]]*.*\([^)]*[A-Z][a-zA-Z0-9]*[^)]*\)/ && !/^\s*\/\// {
            # Check if @Nullable is on previous line
            if (prev ~ /@Nullable/) { in_method=0; next }
            # Only public/protected, not private
            in_method=1
            method_line=NR
            method_sig=$0
            check_count=0
            next
        }
        in_method && /^\s*{/ {
            in_method=2
            next
        }
        in_method==2 {
            # First few lines of method body
            if (check_count < 3) {
                if (/Objects\.requireNonNull|if.*null|throw.*NullPointer/) {
                    in_method=0
                } else {
                    check_count++
                }
            }
            if (/^\s*}/ && check_count >= 3) {
                # Likely missing null check
                print FILENAME":"method_line":"method_sig
                in_method=0
            }
        }
        { prev=$0 }
    ' {} \; 2>/dev/null | head -50
} > "$REVIEW_OUTPUT_DIR/brutal-missing-null-checks.txt" 2>/dev/null || true

NULL_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-missing-null-checks.txt" 2>/dev/null | wc -l)
if [ "$NULL_COUNT" -gt 0 ]; then
    echo "✗ Found $NULL_COUNT public methods potentially missing null checks"
else
    echo "✓ Null checking looks good"
fi

# 7. MUTABLE STATIC FIELDS
echo "[7/10] Hunting for mutable static state..."
{
    echo "=== Mutable Static Fields (not final) ==="
    find src/main/java -name "*.java" -exec grep -Hn "private static [^f].*=\|public static [^f].*=" {} \; | grep -v "final\|Logger" || true
} > "$REVIEW_OUTPUT_DIR/brutal-mutable-static.txt" 2>/dev/null || true

# Count actual findings (skip the header and empty lines)
MUTABLE_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-mutable-static.txt" 2>/dev/null | grep -c . || true)
if [ "$MUTABLE_COUNT" -gt 0 ]; then
    echo "✗ Found $MUTABLE_COUNT mutable static fields"
else
    echo "✓ No mutable static state"
fi

# 8. RESOURCE LEAKS
echo "[8/10] Checking for resource leaks..."
{
    echo "=== Potential Resource Leaks (no try-with-resources) ==="
    # Use Python for accurate multiline try-with-resources detection
    if command -v python3 &> /dev/null; then
        python3 "$SCRIPT_DIR/check_resource_leaks.py" 2>/dev/null || true
    else
        # Fallback grep-based detection (less accurate for multiline patterns)
        find src/main/java -name "*.java" -exec grep -Hn "new.*Stream\|new.*Reader\|new.*Writer\|new.*Connection" {} \; | grep -v "try (" || true
    fi
} > "$REVIEW_OUTPUT_DIR/brutal-resource-leaks.txt" 2>/dev/null || true

LEAK_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-resource-leaks.txt" 2>/dev/null | grep -c . || true)
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

BROAD_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-broad-exceptions.txt" 2>/dev/null | grep -c . || true)
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

OVERRIDE_COUNT=$(tail -n +2 "$REVIEW_OUTPUT_DIR/brutal-missing-override.txt" 2>/dev/null | grep -c . || true)
if [ "$OVERRIDE_COUNT" -gt 0 ]; then
    echo "✗ Found $OVERRIDE_COUNT missing @Override annotations"
else
    echo "✓ @Override annotations look good"
fi

echo ""
echo "========================================="
echo "BRUTAL Review Complete - $(date)"
echo "========================================="

# Count total findings (skip header lines starting with === and empty lines)
TOTAL_FINDINGS=0
for file in "$REVIEW_OUTPUT_DIR"/brutal-*.txt; do
    if [ -f "$file" ] && [ -s "$file" ]; then
        COUNT=$(tail -n +2 "$file" | grep -c . || true)
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
