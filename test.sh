#!/usr/bin/env bash
set -e

# ==============================================================================
# HCMS Test Suite Runner
# ==============================================================================

echo "=========================================================="
echo " Running HCMS Automated Test Suite (Surefire & Failsafe) "
echo "=========================================================="

mvn clean test

echo "[+] Unit test suite passed with 100% success."
