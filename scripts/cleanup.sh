#!/usr/bin/env bash
# cleanup.sh — removes the travel-app namespace and everything inside it
set -euo pipefail

read -p "This will delete the entire travel-app namespace. Continue? (y/N) " yn
case "$yn" in
    y|Y) ;;
    *) echo "Aborted."; exit 0;;
esac

kubectl delete namespace travel-app --ignore-not-found
echo "✅ Cleanup complete."
