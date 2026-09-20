# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Pac-Man written four times against raylib, once per Clojure-family runtime:
  `babashka-example` over `babashka.ffi`, `clojure-example` over raylib-clj
  (coffi / Panama), `jank-example` over jank's `cpp/` interop, and
  `jolt-example` over `net.b12n/raylib` on `jolt.ffi`. Each directory is a
  standalone project that runs from its own build file.
- A root `bb.edn` that runs any port, sweeps all four (`check-all`,
  `run-all`, `shot-all`, `doctor-all`), and drives the demo recorder from one
  registry.
- A still and an animated preview per port under `docs/demos/`, both committed.
- `docs/guide/` covering the shared game, the FFI comparison, the two ways to
  draw Pac-Man, and one page per runtime, rendered by
  [docs-engine](https://github.com/jlt-commons/docs-engine).
- EPL-2.0 licence.

### Fixed

- All four ports read raylib's key queue (`GetKeyPressed`) alongside the held
  key state. `IsKeyDown` and `IsKeyPressed` read polled state, so a press
  shorter than one frame was invisible to both; the queue records it. A quick
  tap now buffers a turn.
