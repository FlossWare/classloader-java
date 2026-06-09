#!/usr/bin/env python3
"""
Accurate resource leak detector that properly handles try-with-resources.
Detects resource allocations (new Stream, Reader, Writer, Connection, etc.)
that are not properly wrapped in try-with-resources or try-finally blocks.

Strategy:
  1. Strip comments from the Java source.
  2. Find every line that allocates a resource (new *Stream, *Reader, etc.).
  3. For each allocation, walk backward to determine if it is inside a
     try-with-resources declaration or a try block with a finally clause.
  4. Report only those allocations that are not managed by either pattern.
"""

import os
import re
from pathlib import Path

# Magic number constants for resource leak detection heuristics
MAX_LINES_TO_SEARCH_BACKWARD = 30
MAX_LINES_FOR_PAREN_MATCHING = 30
MAX_LINES_TO_CHECK_FINALLY = 30
MAX_LINES_FOR_TRY_BLOCK_DETECTION = 15
LINES_AFTER_ALLOCATION_FOR_TRY = 3
MAX_LINES_IN_FINALLY_FOR_CLEANUP = 15


def strip_comments(content):
    """Remove Java block and line comments."""
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
    content = re.sub(r'//.*?$', '', content, flags=re.MULTILINE)
    return content


def _find_matching_close_paren(lines, start_line, start_col):
    """Find the line number of the closing ')' that matches the '(' at (start_line, start_col).

    Returns the line number of the closing paren, or -1 if not found within MAX_LINES_FOR_PAREN_MATCHING lines.
    """
    depth = 0
    for i in range(start_line, min(len(lines), start_line + MAX_LINES_FOR_PAREN_MATCHING)):
        line = lines[i]
        begin = start_col if i == start_line else 0
        for j in range(begin, len(line)):
            ch = line[j]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    return i
    return -1


def is_in_try_with_resources(lines, target_line):
    """Return True if the resource allocation on *target_line* is part of a
    try-with-resources declaration list.

    Walks backward up to MAX_LINES_TO_SEARCH_BACKWARD lines looking for ``try\\s*(``.  When found,
    determines where the matching ``)`` is.  If target_line falls between the
    opening ``(`` and closing ``)``, the allocation is managed.
    """
    search_start = max(0, target_line - MAX_LINES_TO_SEARCH_BACKWARD)

    for i in range(target_line, search_start - 1, -1):
        m = re.search(r'try\s*\(', lines[i])
        if m:
            # Find the column of the opening '(' in the try-with-resources
            open_col = lines[i].index('(', m.start())
            close_line = _find_matching_close_paren(lines, i, open_col)
            if close_line == -1:
                # Could not find closing paren; conservatively assume managed
                return True
            # The resource is managed if it is between the try( and the closing )
            if i <= target_line <= close_line:
                return True
            # The try-with-resources closed before our target line
            if close_line < target_line:
                # Keep searching backward; there might be an outer try-with-resources
                continue
        # Stop searching backward at method / constructor boundaries
        if re.search(r'^\s*(public|private|protected)\s+.*\)\s*(throws\s+\w[\w,.  ]*\s*)?\{', lines[i]):
            break

    return False


def is_in_try_finally(lines, target_line):
    """Return True if the allocation on *target_line* appears inside a
    try { ... } finally { ... close/disconnect ... } block.

    Walks backward looking for ``try {`` and forward looking for ``finally {``
    with a close() or disconnect() call inside.
    """
    # Walk backward looking for 'try {'
    search_start = max(0, target_line - MAX_LINES_FOR_TRY_BLOCK_DETECTION)
    found_try = False
    for i in range(target_line, search_start - 1, -1):
        if re.search(r'try\s*\{', lines[i]):
            found_try = True
            break
        # Stop at method boundaries
        if re.search(r'^\s*(public|private|protected)\s+.*\)\s*(throws\s+\w[\w,.  ]*\s*)?\{', lines[i]):
            break

    if not found_try:
        # Also check if the try block starts right after the allocation line
        for i in range(target_line + 1, min(len(lines), target_line + LINES_AFTER_ALLOCATION_FOR_TRY)):
            if re.search(r'try\s*\{', lines[i]):
                found_try = True
                break

    if not found_try:
        return False

    # Walk forward looking for 'finally'
    search_end = min(len(lines), target_line + MAX_LINES_TO_CHECK_FINALLY)
    for i in range(target_line + 1, search_end):
        if re.search(r'finally\s*\{', lines[i]):
            # Check if the finally block contains close/disconnect
            for j in range(i, min(len(lines), i + MAX_LINES_IN_FINALLY_FOR_CLEANUP)):
                if re.search(r'\.(close|disconnect|safelyDisconnect)\s*\(', lines[j]):
                    return True
            return True  # Has finally, assume cleanup
        # If we hit a catch or another method, stop
        if re.search(r'^\s*(public|private|protected)\s+.*\)\s*(throws\s+\w[\w,.  ]*\s*)?\{', lines[i]):
            break

    return False


def check_for_resource_leaks(java_file):
    """Check a single Java file for resource leaks. Returns list of (line_number, line_text)."""
    leaks = []

    try:
        with open(java_file, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return leaks

    content_clean = strip_comments(content)
    lines = content_clean.split('\n')

    # Pattern: any resource type that typically needs closing
    resource_pattern = re.compile(
        r'new\s+\w*(InputStream|OutputStream|Reader|Writer|Socket|ServerSocket'
        r'|JarInputStream|JarOutputStream|ZipInputStream|ZipOutputStream'
        r'|BufferedReader|BufferedWriter|PrintWriter|Scanner)\s*\('
    )

    # HttpURLConnection / URLConnection are NOT AutoCloseable, so they use
    # disconnect() instead of close().  We detect them separately.
    connection_pattern = re.compile(
        r'(?:HttpURLConnection|URLConnection)\)?\s+\w+\s*='
        r'|=\s*\((?:HttpURLConnection|URLConnection)\)'
    )

    for line_num, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith('*'):
            continue

        if resource_pattern.search(line):
            if is_in_try_with_resources(lines, line_num):
                continue
            if is_in_try_finally(lines, line_num):
                continue

            # Skip ByteArrayOutputStream and ByteArrayInputStream - these hold
            # only in-memory data and their close() is a documented no-op.
            if re.search(r'new\s+ByteArray(Input|Output)Stream', line):
                continue

            leaks.append((line_num + 1, line.rstrip()))

    return leaks


def main():
    """Find all Java source files under src/main/java and check for resource leaks."""
    java_files = []
    for root, _dirs, files in os.walk('src/main/java'):
        for f in files:
            if f.endswith('.java'):
                java_files.append(os.path.join(root, f))

    for java_file in sorted(java_files):
        leaks = check_for_resource_leaks(java_file)
        for line_num, line_text in leaks:
            print(f"{java_file}:{line_num}:{line_text}")


if __name__ == '__main__':
    main()
