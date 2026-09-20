#!/usr/bin/env sh
# Launch one example for screen-grab, the internal capture tool (not public
# yet). Nothing else uses this script, and nothing in the repo needs it to
# build: the GIFs it helps produce are committed.
#
#   scripts/run-example.sh <example-dir> <seconds> <command> [args...]
#
# Two things this exists for, both measured rather than assumed:
#
# 1. screen-grab's :run is TOKENIZED, not handed to a shell, so a
#    `cd X && ...` in the manifest would exec `cd` with the rest as its
#    arguments. This wrapper is the shell that string cannot be.
#
# 2. `exec` keeps the pid. screen-grab targets the pid it spawned, and a
#    capture aimed at a process that owns no window waits forever. Running
#    the real command through `exec` means the spawned pid IS the window
#    owner for babashka (bb), Clojure (the clojure script execs java) and
#    jolt. Only jank cannot collapse that far, because lein starts the
#    compiled binary as a child, so that one item names its app instead.
#
#    This is also why the command is passed in rather than going through the
#    example's own `bb pacman`: a bb task SPAWNS its child, so the pid would
#    be bb's and the window would belong to something else.
set -eu

dir=${1:?usage: run-example.sh <example-dir> <seconds> <command> [args...]}
secs=${2:?usage: run-example.sh <example-dir> <seconds> <command> [args...]}
shift 2
[ "$#" -gt 0 ] || { echo "run-example.sh: no command given" >&2; exit 2; }

cd "$(dirname "$0")/.." || exit 1
cd "$dir" || exit 1
exec "$@" "$secs"
