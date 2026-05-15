#!/bin/sh
# Gradle wrapper script
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
exec gradle "$@" -p "$SCRIPT_DIR"
