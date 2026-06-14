#!/usr/bin/env bash
set -euo pipefail

detect_out="$(bash scripts/fetch-latest-paper-version.sh)"
latest="$(printf '%s\n' "$detect_out" | head -n1)"
current="$(sed -n 's/^paper\.version=//p' gradle.properties)"

if [ "$latest" = "$current" ]; then
  echo "paper.version already up to date: $current"
  exit 0
fi

echo "Bumping paper.version: $current -> $latest"
tmp="$(mktemp)"
sed "s/^paper\.version=.*/paper.version=$latest/" gradle.properties > "$tmp"
mv "$tmp" gradle.properties

git config user.email "park@junhyung.kr"
git config user.name "junhyung.ci"
git add gradle.properties
git commit -m "Bump Paper to $latest"

: "${GITHUB_TOKEN:?GITHUB_TOKEN env var is required to push the bump commit}"
remote="$(git remote get-url origin)"
remote="${remote#https://}"
remote="${remote#*@}"
git push "https://x-access-token:${GITHUB_TOKEN}@${remote}" HEAD:main
