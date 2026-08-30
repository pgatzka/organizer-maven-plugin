#!/usr/bin/env bash
# Every goal the plugin declares must be documented in README.md and docs/goals.md.
# A goal nobody can find is a goal nobody uses.
set -euo pipefail

descriptor="target/classes/META-INF/maven/plugin.xml"
if [[ ! -f "$descriptor" ]]; then
  echo "No plugin descriptor at $descriptor; run 'mvn process-classes' first." >&2
  exit 1
fi

goals=$(sed -n 's|.*<goal>\(.*\)</goal>.*|\1|p' "$descriptor" | sort -u)
missing=0

for goal in $goals; do
  for doc in README.md docs/goals.md; do
    if ! grep -q "organizer:$goal" "$doc"; then
      echo "The goal '$goal' is not documented in $doc" >&2
      missing=1
    fi
  done
done

if [[ $missing -eq 1 ]]; then
  echo >&2
  echo "Add the missing goals to the guides, then run this again." >&2
  exit 1
fi

echo "All $(echo "$goals" | wc -w | tr -d ' ') goals are documented."
