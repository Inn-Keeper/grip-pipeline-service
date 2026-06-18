#!/bin/bash
# Load .env and run the app. Usage: ./dev-run.sh [gradle_args...]
set -e

if [ ! -f .env ]; then
  echo "Error: .env file not found. Copy from .env.example and fill in your secrets."
  exit 1
fi

# Load all non-comment, non-empty lines from .env into environment
export $(grep -v '^\s*#' .env | grep -v '^\s*$' | xargs)

echo "✓ Environment loaded from .env"
echo "  Running: ./gradlew ${@:-bootRun}"

./gradlew "${@:-bootRun}"
