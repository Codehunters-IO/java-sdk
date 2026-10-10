#!/usr/bin/env bash
# Refuses a BOM release while any library it lists is missing from GitHub Packages.
#
# Run by the shared java-library-publish workflow (`verify_script`) after it has checked the tag
# and resolved the module, from a checkout at the tag. Library tags pass through.
#
# Needs: TAG, GH_PACKAGES_USERNAME and GH_PACKAGES_TOKEN.
set -euo pipefail

tag="${TAG:?TAG is required}"
[[ "$tag" == bom/* ]] || exit 0

repository_url="$(awk -F= '$1 == "github_packages_url" { print $2 }' gradle.properties)"
missing=()
while IFS='=' read -r artifact version; do
  case "$artifact" in '' | '#'* | bom.version) continue ;; esac
  pom="$repository_url/io/codehunters/$artifact/$version/$artifact-$version.pom"
  if ! curl -sfI -o /dev/null -u "${GH_PACKAGES_USERNAME:?}:${GH_PACKAGES_TOKEN:?}" "$pom"; then
    missing+=("$artifact:$version")
  fi
done < platform/sdk-bom/bom-lock.properties

if [ "${#missing[@]}" -gt 0 ]; then
  echo "::error::publish these releases before $tag: ${missing[*]}" >&2
  exit 1
fi
