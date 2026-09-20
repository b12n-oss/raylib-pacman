# Running unattended

A game is one of the harder things to verify automatically. There is no
assertion to write about whether Pac-Man looks right, synthetic clicks do not
actuate a raylib window, and everything interesting only exists for one frame at
a time. So the bar here is lower and more honest than a test suite: every
example can run itself for a fixed time and drop a PNG of a chosen frame, with
nobody at the keyboard.

All four take the same three optional arguments:

```sh
bb pacman                  # play it, window stays open
bb pacman 10               # quit after ten seconds of game time
bb pacman 3 out.png 76     # quit after three, capture frame 76 to out.png
```

## The clock is game time, not wall time

This is the one design decision on this page that was not obvious at the start,
and jank is what forced it.

The deadline sums `:clock`, which accumulates the same clamped `dt` the physics
uses:

```clojure
(min 0.05 (get-frame-time))
```

rather than reading a wall clock. The clamp already exists so a stalled frame
cannot step an entity through a wall, and reusing it here means a single slow
frame contributes 0.05 seconds to the deadline instead of however long it
actually took.

That matters because jank compiles a fn the first time it is called. Frame 0
therefore pays for the entire draw path at once. Measured on an M1 Pro:

```
DEBUG f 0   gt 0.53      <- first frame begins
DEBUG f 20  gt 6.86      <- 6.3s for the first twenty frames
DEBUG f 40  gt 7.20      <- 0.34s per twenty thereafter, a steady 60 FPS
DEBUG f 60  gt 7.53
DEBUG f 80  gt 7.88
```

With a wall clock and a three second deadline, the run exited after frame 1 with
a score of 0 and no screenshot. Nothing errored. The output was a window that
appeared and vanished, which reads exactly like a game that does not work, and
the actual cause was a healthy runtime being timed with the wrong instrument.

The other three runtimes do not have a warm-up this large, but they all use the
game clock too. Consistency is worth more than the microseconds, and a wall
clock is a latent version of the same trap anywhere a JIT is involved.

## Flush before you capture

raylib batches geometry and submits it at `EndDrawing`. A `TakeScreenshot`
called mid-frame captures whatever was already flushed, which for a frame drawn
entirely in immediate mode is nothing at all.

So every port flushes first:

```clojure
(rl-draw-render-batch-active)    ;; rlDrawRenderBatchActive
(take-screenshot shot-path)
```

Skip it and you get a plausible-looking PNG of an empty background, which is
worse than an error because it looks like a rendering bug in the game.

## Pass a basename, not a path

raylib resolves a screenshot path against the working directory it captured at
`InitWindow`, not the live one. An absolute path gets appended to that base and
produces something like `<base>//tmp/x.png`, which fails with a warning **and a
zero exit code**.

Every example here passes a basename and lets the file land next to the project.
The root `bb demos` task moves them into `docs/demos/` afterwards.

## What this catches, and what it does not

It catches: a window that will not open, a namespace that will not load, a
binding whose symbol is missing, a crash in the first seconds of play, a shape
drawn at the wrong coordinates, a colour with its bytes swapped, and a sector
swept from the wrong angle. That last group is the whole reason for the PNG,
since none of them produce an error.

It does not catch anything about input. Nothing here has been steered by a test
and nothing can be: synthetic key events do not actuate a raylib window. The
honest claim for these captures is "it renders correctly", never "it plays
correctly". Steering, the buffered turn, and the ghost chase all still need a
person.

It also will not catch a bug that takes longer than the capture to appear. Three
seconds is roughly 180 frames, which is enough to see Pac-Man cross half the
maze and eat a power pellet, and not enough to see a level cleared.

## The animated previews

The GIFs under `docs/demos/` come from a different mechanism to the PNGs above:
`screen-grab`, driven by the manifest in `scripts/demo_manifest.edn`, which
launches each port, steers it with synthetic keystrokes and records its window.

**`screen-grab` and `cgevent` are internal b12n tools and are not public yet**,
so `bb record` only runs on a machine that has them. That deliberately does not
matter to anyone else: every GIF is committed, so the docs site, the README
gallery and this guide all build with no capture toolchain installed at all.
The manifest is committed for the same reason a build script is, to say how the
previews were made rather than to leave them as artifacts nobody can reproduce.

One finding from that work belongs here rather than in the manifest, because it
is about raylib and not about the recorder. See
[the-game.md](the-game.md) for where it landed in the code.

A synthetic keystroke has no duration. `IsKeyDown` and `IsKeyPressed` both read
polled state, and `PollInputEvents` copies current to previous before letting
GLFW's callback update current, so a press that goes down and up inside one
poll leaves no trace in either. The game saw nothing, and the first recordings
were eight seconds of Pac-Man parked against a wall.

ESC closed the window the whole time, which is what gave it away: raylib checks
the exit key inside the GLFW callback rather than from polled state. The queue
works the same way. The callback appends every key-down to
`keyPressedQueue`, and only the app drains it, so `GetKeyPressed` sees a tap
that `IsKeyDown` cannot. All four ports now read the queue as well as the held
state, which makes a quick tap register for a person too.

## Running the sweep

From the repository root:

```sh
bb check-all      # compile or load all four
bb shot-all       # run each for 3 seconds, capture a PNG
bb run-all 10     # cycle all four as a demo reel
bb demos          # refresh docs/demos/*.png
```

Each of those delegates to the example's own `bb.edn` rather than
reimplementing the build, so the directories stay independent. The sweep reports
every result and exits non-zero if any failed, instead of stopping at the first
one, because knowing that three of four work is more useful than knowing the
first one broke.
