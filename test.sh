#!/usr/bin/env bash
# Copyright (C) 2026 Toit contributors.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
build_dir="$repo_root/build/policy-tests"
source_dir="$repo_root/src/org/floitsch/netflixjellyfin"
mkdir -p "$build_dir"
javac --release 8 -d "$build_dir" \
  "$source_dir/NightSchedule.java" "$source_dir/NightGate.java" \
  "$source_dir/ProximityGate.java" "$source_dir/AudioFallback.java" \
  "$repo_root/test/PolicyTest.java"
java -cp "$build_dir" org.floitsch.netflixjellyfin.PolicyTest
