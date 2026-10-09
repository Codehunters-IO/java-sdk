#!/usr/bin/env bash
# Pulls the container images the integration tests start, before Gradle runs.
# Testcontainers pulls lazily and reports a failed pull only as ContainerFetchException after a
# five-minute wait. Docker Hub's token endpoint (auth.docker.io) can time out from GitHub runners,
# so each image is tried on Docker Hub once and then on public mirrors of Docker Hub. A mirrored
# image is tagged with its Docker Hub name, which is the name Testcontainers looks up locally.
set -euo pipefail

images=(
  "testcontainers/ryuk:0.14.0"
  "postgres:17-alpine"
  "imresamu/postgis:17-3.5-alpine"
  "rabbitmq:4.1-alpine"
  "apache/kafka:4.1.1"
)

mirrors=(
  "mirror.gcr.io"
  "public.ecr.aws/docker"
)

# Docker Hub names official images without the library/ namespace; the mirrors need it.
qualified() {
  local image="$1"
  if [[ "$image" == */* ]]; then echo "$image"; else echo "library/$image"; fi
}

pull_from_mirror() {
  local image="$1" mirror mirrored
  for mirror in "${mirrors[@]}"; do
    mirrored="$mirror/$(qualified "$image")"
    if docker pull --quiet "$mirrored"; then
      docker tag "$mirrored" "$image"
      echo "pulled $image from $mirror"
      return 0
    fi
    echo "::warning::docker pull $mirrored failed"
  done
  return 1
}

pull() {
  local image="$1" attempt
  for attempt in 1 2; do
    if docker pull --quiet "$image" || pull_from_mirror "$image"; then
      return 0
    fi
    echo "::warning::could not pull $image from Docker Hub or its mirrors (attempt $attempt)"
    sleep 30
  done
  echo "::error::could not pull $image"
  return 1
}

for image in "${images[@]}"; do
  pull "$image"
done
