#!/usr/bin/env bash
# Checks a published release tag and prints the Gradle path of the module it releases.
#
# Run from a full clone (tags fetched) checked out at the tag. Fails when:
# - the tag is not `sdk-<module>/vX.Y.Z` or `bom/vX.Y.Z`;
# - the tagged commit is not on origin/master (or origin/main);
# - no module at that commit declares that version (`./gradlew releaseCandidate`);
# - it is a BOM whose libraries are not all in GitHub Packages yet.
#
# Needs: TAG, and for BOM tags GH_PACKAGES_USERNAME and GH_PACKAGES_TOKEN.
set -euo pipefail

tag="${TAG:?TAG is required}"
tag_pattern='^(sdk-[a-z0-9-]+|bom)/v[0-9]+\.[0-9]+\.[0-9]+$'

fail() {
  echo "::error::$1" >&2
  exit 1
}

[[ "$tag" =~ $tag_pattern ]] || fail "'$tag' is not a release tag (sdk-<module>/vX.Y.Z or bom/vX.Y.Z)"

on_base_branch=false
for branch in origin/master origin/main; do
  if git rev-parse -q --verify "$branch" > /dev/null &&
    git merge-base --is-ancestor "refs/tags/$tag" "$branch"; then
    on_base_branch=true
  fi
done
[ "$on_base_branch" = true ] || fail "$tag points at a commit that is not on master: release only merged code"

./gradlew releaseCandidate --quiet > /dev/null
path="$(cat build/release-candidates/*.txt | awk -v tag="$tag" '$1 == tag { print $2 }')"
[ -n "$path" ] || fail "no module declares the version in $tag at the tagged commit"

if [[ "$tag" == bom/* ]]; then
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
    fail "publish these releases before $tag: ${missing[*]}"
  fi
fi

echo "$path"
