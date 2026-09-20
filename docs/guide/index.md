# Guide

Four ports of the same game, one per Clojure-family runtime, kept side by side
so the differences are readable. The game itself is a pretext. What the repo is
actually for is showing what changes when the same Clojure crosses into C four
different ways, and what does not.

Read in this order the first time:

| Page | What it covers |
|---|---|
| [the-game.md](the-game.md) | The half that barely changes. State, the frame step, tile-centre movement, the four ghost personalities. |
| [crossing-to-c.md](crossing-to-c.md) | The comparison. How each runtime reaches raylib, what each FFI will and will not carry, and what that costs. |
| [drawing-pac-man.md](drawing-pac-man.md) | One shape, two implementations, and the angle convention that breaks silently. |
| [running-unattended.md](running-unattended.md) | Deadlines, screenshots, and why the clock is game time rather than wall time. |

Then the per-runtime pages, which assume the four above:

| Page | Runtime |
|---|---|
| [babashka.md](babashka.md) | babashka over `babashka.ffi` |
| [clojure.md](clojure.md) | Clojure on the JVM over raylib-clj, coffi and Panama |
| [jank.md](jank.md) | jank over `cpp/` interop |
| [jolt.md](jolt.md) | jolt over `jolt.ffi` |

Each example directory also carries its own `README.md`, which is the short
operational version: how to run it, what it needs installed, what bites.

## A note on what is measured here

Every claim in these pages about a runtime's behaviour was run rather than
remembered, on macOS 15 and an Apple M1 Pro, in September 2026. Where a number
appears, it came from a probe in this repo. Where something is described as
failing, it failed and the error text is quoted. The versions in play were
babashka 1.13.220, jank 0.1-alpha with `raylib-sys 2026.09-3`, jolt 0.8.10, and
raylib 6.0 from Homebrew.

That matters most for the jank page. jank is alpha and moves quickly, so a
constraint described there may have been lifted since.
