#!/usr/bin/env bash
set -e

# ==============================================================================
# HCMS Startup Script
# ==============================================================================

echo "=========================================================="
echo " Starting Hostel Complaint & Maintenance System (HCMS) "
echo "=========================================================="

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    echo "[+] Docker daemon detected. Starting with Docker Compose..."
    docker compose up --build
else
    echo "[!] Docker daemon not available or not running."
    echo "[+] Falling back to local execution with embedded Javalin server..."
    
    if [ ! -f "hcms-web/target/hcms-web-1.0.0.jar" ]; then
        echo "[+] Building fat JAR..."
        mvn clean package -DskipTests
    fi

    echo "[+] Launching HCMS on http://localhost:8080..."
    java -jar hcms-web/target/hcms-web-1.0.0.jar
fi
