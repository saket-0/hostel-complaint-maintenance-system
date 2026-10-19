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
        echo "=========================================================="
        ;;
esac
