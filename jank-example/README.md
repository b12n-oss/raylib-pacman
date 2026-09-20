# jank-example

Pac-Man on raylib, called from [jank](https://jank-lang.org) over its `cpp/`
interop. jank is a native Clojure dialect on LLVM, so there is no JVM here and
raylib is reached by including `raylib.h` directly.

```sh
bb info                   # every task here
bb pacman                 # play it
bb pacman 10              # quit after ten game-seconds
bb pacman 3 out.png 76    # quit after three, capture frame 76
bb check                  # compile the namespace
bb clean                  # drop the native build cache
bb doctor                 # jank, lein and libraylib
```

Or with Leiningen directly, which is what the tasks wrap:

```sh
lein run --disable-sandbox
```

Arrows or WASD steer, ENTER restarts after GAME OVER.

## Prerequisites

`jank`, a Leiningen, and a system raylib 6.0 or newer. raylib itself comes from
the published `org.jank-lang.commons/raylib-sys` package, so there is no wrapper
to build by hand, but the first run compiles it natively and takes a few
minutes.

**`--disable-sandbox` is required on macOS.** There is no `bwrap`, so the native
build has to run unsandboxed. Leaving it off aborts with `No 'bwrap' executable
found`, which is not a jank error and never mentions jank.

The project layout follows the
[raylib-sys example](https://github.com/jank-lang/commons/tree/main/raylib-sys/example)
in jank's commons repo: `project.clj` with `lein-jank` as a plugin and
middleware, sources under `src/`, and debug and release profiles that differ
only in optimization level.

## Four things this port had to work around

Each was measured against jank 0.1-alpha rather than assumed, and each one
shaped the code.

**A jank fn may not return a native value.** A raylib `Color` is one. Colours
therefore travel as packed integers and the `Color` is rebuilt with `GetColor`
inline at every draw call, which is a position jank does allow. `rect!`,
`circle!`, `sector!` and `text!` at the top of the file exist to make that
one-line-per-call rather than one-line-per-use.

**`case` does not compile when its result arms are native-shaped.** Returning
integers from a `case` fails inside `clojure/core.jank` with `no viable
overloaded '='`, naming neither your form nor your file. Every dispatch in this
port is a `cond` for that reason, including the one picking each ghost's target.

**`mod` returns a real, `quot` does not.** `(mod 7 3)` is `1.0`. Feeding that to
a C `int` parameter throws `invalid object type (expected integer found
small_real)`, so anything used as an index or handed to `cpp/int` goes through
`int` first. `tile-at` is where this bites, since the column wraps with `mod`.

**There is no JVM.** No `Math/*`, no `format`, no `parse-long`. C maths arrives
through `math.h` as `cpp/sin`, `cpp/atan2`, `cpp/floor` and `cpp/fabs`, `str`
replaces `format`, and `cpp/TextToInteger` stands in for `parse-long`.

## `lein run` is slow to start; the binary it builds is not

Under `lein run`, frame 0 pays for the whole draw path at once. On an M1 Pro
that is about 6.3 seconds for the first twenty frames, after which it holds a
steady 60 FPS (20 frames per 0.34s).

That is the dev path, not the language. The AOT binary Leiningen leaves behind
has no measurable warm-up:

```sh
lein run --disable-sandbox 6      # 6 game-seconds, 19.6s wall
./target/debug/jank-example 6     # 6 game-seconds,  6.4s wall, window in ~1s
```

Use `lein run` while editing, since it rebuilds what changed. Use the binary
when you want to see what the code actually costs.

The slow start is also why the deadline counts game time rather than wall time.
A wall clock charges it against the deadline and quits before frame 2, which
looks exactly like a game that does not work.
