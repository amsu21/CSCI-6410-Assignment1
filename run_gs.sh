#!/usr/bin/env bash
set -euo pipefail

# Compile
echo "Compiling..."
javac GSMatching.java

# Run with sample input
echo "Running Gale–Shapley with sample files..."
java GSMatching boys girls couples

# Show result
echo
echo "Output (couples):"
cat couples