#!/usr/bin/env bash

# Gradle Wrapper Proxy for AI Studio Export
# Runs the system-installed gradle command or advises the user.

if command -v gradle >/dev/null 2>&1; then
  gradle "$@"
else
  echo ""
  echo "========================================================================="
  echo "                      Gradle Wrapper Proxy Finder"
  echo "========================================================================="
  echo "Gradle was not found in your system's PATH."
  echo "Please install Gradle (v9.3.1 recommended) or open this project"
  echo "inside Android Studio, which manages Gradle automatically."
  echo "========================================================================="
  echo ""
  exit 1
fi
