# Getting started

What you will have at the end of this page:

- raylib installed, which all four ports link against
- at least one port running in a window with Pac-Man moving
- a sense of which port to reach for first

## Install raylib

None of the four bundles it. Install it once, system-wide:

```sh
brew install raylib                  # macOS
sudo pacman -S raylib                # Arch
sudo apt install libraylib-dev       # Debian/Ubuntu, if new enough
```

Version 6.0 or newer. Anything older is missing calls these examples use.

## Pick a port

Each directory is standalone and needs only its own toolchain. Start with
whichever you already have.

| Directory | Also needs | First run |
|---|---|---|
| `babashka-example/` | `bb` | instant, no build step |
| `clojure-example/` | JDK 22+ (Panama) | a few seconds, plus a git clone |
| `jolt-example/` | `jolt` 0.8.0+ | a few seconds |
| `jank-example/` | `jank` and Leiningen | **several minutes**, it builds raylib natively |

babashka is the fastest way to see something on screen. jank is the slowest to
start and the most interesting to read.

## Run it

From the repository root:

```sh
bb babashka          # or: bb clj, bb jank, bb jolt
```

Or from inside the example, which is the same thing without the indirection:

```sh
cd babashka-example
bb info              # what this example can do
bb pacman            # play it
```

Arrows or WASD steer. ENTER restarts after GAME OVER. Close the window or press
ESC to quit.

If something is missing, ask before guessing:

```sh
bb doctor-all        # every toolchain, with the install command for what is absent
```

## Run it unattended

Every port takes the same optional arguments, which is what makes them testable
with nobody at the keyboard:

```sh
bb pacman 10               # quit after ten seconds of game time
bb pacman 3 out.png 76     # quit after three, capture frame 76 to out.png
```

The seconds are game time, not wall time. [Running
unattended](running-unattended.md) explains why that distinction earns its
keep.

## Check everything at once

```sh
bb check-all         # compile or load all four
bb run-all 10        # cycle all four as a demo reel
```

`bb tasks` lists every command, and `bb info` groups them with a short
description each. Every example directory has its own `bb info` too.

## Where to go next

If you want to understand the code rather than run it, read
[the game](the-game.md) first: it covers the half that barely changes between
runtimes, so the four ports then read as the same file with a different bottom
third. [Crossing to C](crossing-to-c.md) is the part the repo exists for.
