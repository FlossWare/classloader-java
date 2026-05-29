#!/usr/bin/env python3
"""
Create remote issues from code review findings.
Handles both Python (mypy, flake8, bandit) and Java (security, TODOs) findings.
Uses GitHub CLI (gh) to create issues.
"""

import sys
import json
import subprocess
import tempfile
from pathlib import Path
from datetime import datetime

REVIEW_OUTPUT_DIR = Path(__file__).parent.parent / "review-output"
REPO = "FlossWare/classloader-java"
ISSUE_LABEL = "automated-review"
ISSUE_PREFIX = "[Auto Review]"


def run_gh_command(cmd):
    """Run a gh CLI command and return the output."""
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            check=True
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"Error running command: {cmd}", file=sys.stderr)
        print(f"Error: {e.stderr}", file=sys.stderr)
        return None


def get_existing_issues():
    """Get all existing automated review issues."""
    cmd = f'gh issue list --repo {REPO} --label "{ISSUE_LABEL}" --json number,title,state --limit 1000'
    output = run_gh_command(cmd)
    if output:
        return json.loads(output)
    return []


def parse_mypy_output(file_path):
    """Parse mypy output and extract issues."""
    if not file_path.exists() or file_path.stat().st_size == 0:
        return []

    issues = []
    with open(file_path) as f:
        content = f.read()
        if "error:" in content.lower():
            error_lines = [line for line in content.split('\n') if 'error:' in line.lower()]
            if error_lines:
                issues.append({
                    "title": f"{ISSUE_PREFIX} MyPy found {len(error_lines)} type errors",
                    "body": f"MyPy found type checking issues in Python code.\n\n```\n{content[:2000]}\n```\n\nSee `.claude/review-output/mypy.txt` for full details.",
                    "type": "mypy"
                })
    return issues


def parse_flake8_output(file_path):
    """Parse flake8 output and extract issues."""
    if not file_path.exists() or file_path.stat().st_size == 0:
        return []

    issues = []
    with open(file_path) as f:
        lines = [line.strip() for line in f if line.strip()]

    if lines:
        issues.append({
            "title": f"{ISSUE_PREFIX} Flake8 found {len(lines)} style/quality issues",
            "body": f"Flake8 found code style and quality issues in Python code.\n\n```\n{chr(10).join(lines[:20])}\n```\n\nSee `.claude/review-output/flake8.txt` for full details.",
            "type": "flake8"
        })
    return issues


def parse_bandit_output(file_path):
    """Parse bandit output and extract issues."""
    if not file_path.exists() or file_path.stat().st_size == 0:
        return []

    issues = []
    with open(file_path) as f:
        content = f.read()
        if "issue" in content.lower() or "severity" in content.lower():
            issues.append({
                "title": f"{ISSUE_PREFIX} Bandit found security vulnerabilities in Python",
                "body": f"Bandit security scanner found potential vulnerabilities in Python code.\n\n```\n{content[:2000]}\n```\n\nSee `.claude/review-output/bandit.txt` for full details.",
                "type": "security",
                "labels": ["security", ISSUE_LABEL]
            })
    return issues


def parse_java_security_scans(file_path):
    """Parse Java security scan results."""
    if not file_path.exists() or file_path.stat().st_size == 0:
        return []

    issues = []
    with open(file_path) as f:
        content = f.read()
        java_lines = [line for line in content.split('\n') if '.java:' in line and any(p in line for p in ['System.out', 'printStackTrace', 'Runtime', 'exec'])]

        if java_lines:
            issues.append({
                "title": f"{ISSUE_PREFIX} {len(java_lines)} risky Java code patterns detected",
                "body": f"Found {len(java_lines)} instances of potentially risky patterns in Java code.\n\n```\n{chr(10).join(java_lines[:20])}\n```\n\nSee `.claude/review-output/java-security-scans.txt` for full details.",
                "type": "java-security",
                "labels": ["security", ISSUE_LABEL]
            })
    return issues


def parse_python_security_scans(file_path):
    """Parse Python security scan results."""
    if not file_path.exists() or file_path.stat().st_size == 0:
        return []

    issues = []
    with open(file_path) as f:
        content = f.read()
        python_lines = [line for line in content.split('\n') if '.py:' in line and line.strip() and not line.startswith('===')]

        if python_lines:
            issues.append({
                "title": f"{ISSUE_PREFIX} {len(python_lines)} risky Python code patterns detected",
                "body": f"Found {len(python_lines)} instances of potentially risky patterns in Python code.\n\n```\n{chr(10).join(python_lines[:20])}\n```\n\nSee `.claude/review-output/python-security-scans.txt` for full details.",
                "type": "python-security",
                "labels": ["security", ISSUE_LABEL]
            })
    return issues


def parse_todo_checks(file_path):
    """Parse TODO/FIXME comments."""
    if not file_path.exists() or file_path.stat().st_size == 0:
        return []

    with open(file_path) as f:
        lines = f.readlines()

    if len(lines) > 20:
        issues = [{
            "title": f"{ISSUE_PREFIX} {len(lines)} TODO/FIXME comments found",
            "body": f"Found {len(lines)} TODO/FIXME comments in the codebase.\n\n```\n{''.join(lines[:20])}\n... and {len(lines) - 20} more\n```\n\nSee `.claude/review-output/todo-checks.txt` for full list.",
            "type": "todos"
        }]
        return issues
    return []


def create_github_issue(issue):
    """Create a GitHub issue using a temp file for the body."""
    title = issue["title"]
    body = issue["body"]
    labels = issue.get("labels", [ISSUE_LABEL])

    # Add metadata
    body += f"\n\n---\n*Generated by automated code review on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}*"

    # Write body to temp file to avoid shell escaping issues
    with tempfile.NamedTemporaryFile(mode='w', suffix='.md', delete=False) as f:
        f.write(body)
        body_file = f.name

    try:
        # Create issue using body file
        labels_str = ",".join(labels)
        cmd = f'gh issue create --repo {REPO} --title "{title}" --body-file "{body_file}" --label "{labels_str}"'

        result = run_gh_command(cmd)
        if result:
            print(f"✓ Created issue: {title}")
            return True
        else:
            print(f"✗ Failed to create issue: {title}")
            return False
    finally:
        # Clean up temp file
        Path(body_file).unlink(missing_ok=True)


def main():
    """Main function to process review outputs and create issues."""
    if not REVIEW_OUTPUT_DIR.exists():
        print(f"Review output directory not found: {REVIEW_OUTPUT_DIR}")
        return

    print("Parsing review outputs...")
    all_issues = []

    # Parse each type of output
    parsers = [
        # Python checks
        ("mypy.txt", parse_mypy_output),
        ("flake8.txt", parse_flake8_output),
        ("bandit.txt", parse_bandit_output),
        ("python-security-scans.txt", parse_python_security_scans),
        # Java checks
        ("java-security-scans.txt", parse_java_security_scans),
        # Common checks
        ("todo-checks.txt", parse_todo_checks),
    ]

    for filename, parser in parsers:
        file_path = REVIEW_OUTPUT_DIR / filename
        issues = parser(file_path)
        all_issues.extend(issues)

    if not all_issues:
        print("No issues found in review outputs.")
        return

    print(f"Found {len(all_issues)} potential issues")

    # Get existing issues to avoid duplicates
    existing_issues = get_existing_issues()
    existing_titles = {issue["title"] for issue in existing_issues if issue["state"] == "open"}

    # Create new issues
    created_count = 0
    for issue in all_issues:
        if issue["title"] in existing_titles:
            print(f"⊘ Skipping duplicate: {issue['title']}")
            continue

        if create_github_issue(issue):
            created_count += 1

    print(f"\nCreated {created_count} new issues out of {len(all_issues)} findings")


if __name__ == "__main__":
    main()
