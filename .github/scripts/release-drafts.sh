#!/usr/bin/env bash
# The release notes are Markdown: backticks in the printf formats are literal code spans.
# shellcheck disable=SC2016
# Drafts a GitHub release for every module version on this commit that has no tag yet.
#
# Input: a file of `<tag> <gradle path> <project dir>` lines written by `./gradlew releaseCandidate`.
# A tag that exists was released: skipped. A draft for the tag is moved to this commit with fresh
# notes. Otherwise a new draft is created. Publishing a draft (by a person) creates the tag and
# triggers the publish workflow; nothing here uploads a package.
#
# Needs: GH_TOKEN with contents: write, GITHUB_SHA, a checkout with credentials for ls-remote.
set -euo pipefail

candidates="${1:?usage: release-drafts.sh <candidates file>}"
tag_pattern='^(sdk-[a-z0-9-]+|bom)/v[0-9]+\.[0-9]+\.[0-9]+$'

# Prints the `## <version>` section of a CHANGELOG, without its heading.
changelog_section() {
  local file="$1" version="$2"
  [ -f "$file" ] || return 0
  awk -v version="$version" '
    /^## / {
      heading = $2
      gsub(/[\[\]]/, "", heading)
      sub(/^v/, "", heading)
      if (inside) exit
      inside = (heading == version)
      next
    }
    inside { print }
  ' "$file"
}

draft_state() {
  gh release view "$1" --json isDraft --jq '.isDraft' 2>/dev/null || echo "none"
}

while read -r tag path dir; do
  [ -n "${tag:-}" ] || continue
  if [[ ! "$tag" =~ $tag_pattern ]]; then
    echo "::error::invalid release tag '$tag' from $path"
    exit 1
  fi
  if [ -n "$(git ls-remote --tags origin "refs/tags/$tag")" ]; then
    echo "skip $tag (released)"
    continue
  fi

  version="${tag##*/v}"
  module="${path#:}"
  notes="$(mktemp)"
  changelog_section "$dir/CHANGELOG.md" "$version" > "$notes"
  if [ ! -s "$notes" ]; then
    printf '%s %s. See the CHANGELOG.md of `%s` and the root CHANGELOG.md.\n' "$module" "$version" "$dir" > "$notes"
  fi
  printf '\n---\nPublishing this release uploads `io.codehunters:%s:%s` to GitHub Packages.\n' \
    "$module" "$version" >> "$notes"

  case "$(draft_state "$tag")" in
    true)
      gh release edit "$tag" --target "$GITHUB_SHA" --notes-file "$notes"
      echo "updated draft $tag"
      ;;
    false)
      echo "skip $tag (published, tag pending)"
      ;;
    *)
      gh release create "$tag" --draft --target "$GITHUB_SHA" --title "$module $version" --notes-file "$notes"
      echo "drafted $tag"
      ;;
  esac
done < "$candidates"
