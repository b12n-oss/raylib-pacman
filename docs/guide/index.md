# Guide

Four ports of the same game, one per Clojure-family runtime, kept side by side
so the differences are readable. The game itself is a pretext. What the repo is
actually for is showing what changes when the same Clojure crosses into C four
different ways, and what does not.

New here? [getting-started.md](getting-started.md) gets one of the four on
screen in a couple of minutes.

Read in this order the first time:

| Page | What it covers |
|---|---|
| [getting-started.md](getting-started.md) | Install raylib, pick a port, run it, run it unattended. |
| [architecture.md](architecture.md) | Why four standalone copies rather than one library with four backends, and the shape inside each. |
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

And when you want to change something:

| Page | What it covers |
|---|---|
| [contributing.md](contributing.md) | The gate, why a rendered frame is one of them, and how to add a runtime. |

Each example directory also carries its own `README.md`, which is the short
operational version: how to run it, what it needs installed, what bites.

## Find your scenario

| Scenario | Pages to read |
|---|---|
| "I want to try this out" | [Getting started](getting-started.md) |
| "I want to understand how it works" | [Architecture](architecture.md), then [the game](the-game.md) |
| "I came for the FFI comparison" | [Crossing to C](crossing-to-c.md), then the runtime page you care about |
| "Something renders wrong" | [Drawing Pac-Man](drawing-pac-man.md) |
| "I want to contribute a change" | [Contributing](contributing.md) |

## A note on what is measured here

Every claim in these pages about a runtime's behaviour was run rather than
remembered, on macOS 15 and an Apple M1 Pro, in September 2026. Where a number
appears, it came from a probe in this repo. Where something is described as
failing, it failed and the error text is quoted. The versions in play were
babashka 1.13.220, jank 0.1-alpha with `raylib-sys 2026.09-3`, jolt 0.8.10, and
raylib 6.0 from Homebrew.

That matters most for the jank page. jank is alpha and moves quickly, so a
constraint described there may have been lifted since.
