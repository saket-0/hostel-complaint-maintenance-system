#!/usr/bin/env bash
set -e

# ==============================================================================
# HCMS Fast Verification Tool (Agent & Developer Quick-Check)
# Prevents expensive full builds and avoids spinning up Testcontainers
# ==============================================================================

ACTION="${1:-help}"
PARAM1="$2"
PARAM2="$3"

case "$ACTION" in
    core)
        echo "[+] Fast-compiling hcms-core..."
        mvn -q -pl hcms-core -am test-compile -DskipTests
        echo "[✓] hcms-core compiled successfully."
        ;;
    persistence)
        echo "[+] Fast-compiling hcms-persistence..."
        mvn -q -pl hcms-persistence -am test-compile -DskipTests
        echo "[✓] hcms-persistence compiled successfully."
        ;;
    service)
        echo "[+] Fast-compiling hcms-service..."
        mvn -q -pl hcms-service -am test-compile -DskipTests
        echo "[✓] hcms-service compiled successfully."
        ;;
    web)
        echo "[+] Fast-compiling hcms-web..."
        mvn -q -pl hcms-web -am test-compile -DskipTests
        echo "[✓] hcms-web compiled successfully."
        ;;
    compile|check|all)
        echo "[+] Fast-compiling all modules (skipping tests)..."
        mvn -q test-compile -DskipTests
        echo "[✓] All modules compiled successfully."
        ;;
    test)
        if [ -z "$PARAM1" ]; then
            echo "[!] Error: Missing test class name."
            echo "    Usage: ./quick.sh test <TestClassName> [optional-module]"
            echo "    Example: ./quick.sh test ComplaintManagerTest"
            exit 1
        fi

        if [ -n "$PARAM2" ]; then
            MODULE_PL="hcms-$PARAM2"
            echo "[+] Running test $PARAM1 in $MODULE_PL..."
            mvn -q -pl "$MODULE_PL" -am test -Dtest="$PARAM1" -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
        else
            echo "[+] Running single test: $PARAM1..."
            mvn -q test -Dtest="$PARAM1" -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
        fi
        echo "[✓] Test $PARAM1 completed successfully."
        ;;
    docs)
        echo "[+] Checking documentation sync & path integrity..."
        FAILED=0
        ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        
        # a) Extract backticked file paths and report any that do not exist
        python3 - << 'EOF'
import sys, os, re

root_dir = os.path.abspath(".")
docs = ["AGENTS.md", ".agents/rules/hcms-guide.md", "docs/WORKFLOWS.md", "docs/CODEMAP.md"]
backtick_pattern = re.compile(r'`([^`]+)`')
failed = False

for doc in docs:
    p = os.path.join(root_dir, doc)
    if not os.path.exists(p):
        continue
    with open(p, "r", encoding="utf-8", errors="ignore") as f:
        text = f.read()
    for token in backtick_pattern.findall(text):
        parts = token.strip().split()
        if not parts:
            continue
        # Check candidate path(s)
        items = [token.strip()] if len(parts) == 1 else [parts[0]]
        for item in items:
            clean = item.strip(",;:()'\"")
            if "/" in clean and "." in os.path.basename(clean):
                if any(ch in clean for ch in ['{', '<', '>', '*', '...']) or 'docs/plans/' in clean or clean.startswith('http'):
                    continue
                full_path = os.path.join(root_dir, clean)
                if not os.path.exists(full_path):
                    print(f"[!] Broken path in {doc}: '{clean}' does not exist")
                    failed = True

# b) Report any .agents/workflows/*.md not mentioned in docs/WORKFLOWS.md
workflows_doc = os.path.join(root_dir, "docs", "WORKFLOWS.md")
wf_text = ""
if os.path.exists(workflows_doc):
    with open(workflows_doc, "r", encoding="utf-8") as f:
        wf_text = f.read()

wf_dir = os.path.join(root_dir, ".agents", "workflows")
if os.path.exists(wf_dir):
    for f in os.listdir(wf_dir):
        if f.endswith(".md"):
            base = f[:-3]
            # Match either file reference or /command
            pattern = re.compile(rf'(\.agents/workflows/)?{re.escape(f)}|/{re.escape(base)}\b')
            if not pattern.search(wf_text):
                print(f"[!] Workflow not mentioned in docs/WORKFLOWS.md: .agents/workflows/{f}")
                failed = True

# c) Report any slash command in docs/WORKFLOWS.md without a workflow file
slash_cmds = set()
for m in re.findall(r'`/([a-z]+)(?:\s+[^`]*)?`', wf_text):
    slash_cmds.add(m)
for m in re.findall(r'(?:^|[\s\*`])/([a-z]+)\b(?![/\.])', wf_text):
    if m not in ('api', 'css', 'js', 'images', 'public', 'kebab', 'slug'):
        slash_cmds.add(m)

for cmd in sorted(slash_cmds):
    target_wf = os.path.join(wf_dir, f"{cmd}.md")
    if not os.path.exists(target_wf):
        print(f"[!] Slash command /{cmd} in docs/WORKFLOWS.md has no workflow file: .agents/workflows/{cmd}.md")
        failed = True

if failed:
    sys.exit(1)
EOF
        if [ $? -eq 0 ]; then
            echo "docs OK"
            exit 0
        else
            echo "[!] Documentation check failed."
            exit 1
        fi
        ;;
    *)
        echo "=========================================================="
        echo " HCMS Fast Verification Tool (Agent & Dev Quick-Check)   "
        echo "=========================================================="
        echo "Usage:"
        echo "  ./quick.sh core              - Compile hcms-core"
        echo "  ./quick.sh persistence       - Compile hcms-persistence"
        echo "  ./quick.sh service           - Compile hcms-service"
        echo "  ./quick.sh web               - Compile hcms-web"
        echo "  ./quick.sh check             - Fast-compile all modules"
        echo "  ./quick.sh test <Class>      - Run single test class fast (no containers)"
        echo "                                 e.g. ./quick.sh test ComplaintManagerTest"
        echo "  ./quick.sh test <Class> <mod>- Run test in specific module"
        echo "                                 e.g. ./quick.sh test ComplaintManagerTest service"
        echo "  ./quick.sh docs              - Verify doc links and workflow consistency"
        echo "=========================================================="
        ;;
esac
