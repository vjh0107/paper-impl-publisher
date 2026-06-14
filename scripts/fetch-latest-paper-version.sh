#!/usr/bin/env bash
set -euo pipefail

METADATA="https://repo.papermc.io/repository/maven-public/io/papermc/paper/dev-bundle/maven-metadata.xml"

version="$(curl -fsS --max-time 20 "$METADATA" \
  | grep -oE '[0-9]+(\.[0-9]+)*\.build\.[0-9]+-stable' \
  | sort -V | tail -n1)"

if [ -z "$version" ]; then
  echo "No stable Paper version found" >&2
  exit 1
fi

echo "$version"
echo "##teamcity[setParameter name='paper.version.latest' value='$version']"
