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
- `NOTICE.md`, crediting Michiel Borkent (@borkdude) as the author of the
  original game and reproducing babashka/ffi's MIT notice. All four ports
  descend from `examples/pacman.clj` in that repository, and the attribution
  now says so in the README, in each example's own README, in the guide, and
  at the top of all four source files.

### Fixed

- Corrected the claim that `babashka.ffi` and `jolt.ffi` move scalars only and
  therefore cannot call `DrawCircleSector`. Both pass structs by value, and both
  make that call correctly: babashka binds the centre as
  `[:struct [[:x :float] [:y :float]]]` and passes a map, jolt marks the
  parameter `:by-value` and passes a pointer to a layout buffer. Each was run to
  a rendered frame on 2026-09-21 against babashka 1.13.220 and jolt 0.8.10. The
  two ports keep the rlgl triangle fan, which they inherited from the original
  example rather than being forced into, and the docs now say so in the README,
  both example READMEs, five guide pages and the babashka source. Reported by
  Michiel Borkent (@borkdude).
- All four ports read raylib's key queue (`GetKeyPressed`) alongside the held
  key state. `IsKeyDown` and `IsKeyPressed` read polled state, so a press
  shorter than one frame was invisible to both; the queue records it. A quick
  tap now buffers a turn.
