#!/usr/bin/env bash
# Pulls the container images the integration tests start, with retries, before Gradle runs.
# Testcontainers pulls lazily and reports a failed pull only as ContainerFetchException after a
# five-minute wait; pulling here first surfaces the registry's real error and retries transient ones.
set -euo pipefail

images=(
  "testcontainers/ryuk:0.14.0"
  "postgres:17-alpine"
  "imresamu/postgis:17-3.5-alpine"
  "rabbitmq:4.1-alpine"
  "apache/kafka:4.1.1"
)

pull() {
  local image="$1" attempt
  for attempt in 1 2 3 4; do
    if docker pull --quiet "$image"; then
      return 0
    fi
    echo "::warning::docker pull $image failed (attempt $attempt)"
    sleep $((attempt * 15))
  done
  echo "::error::could not pull $image"
  return 1
}

for image in "${images[@]}"; do
  pull "$image"
done
