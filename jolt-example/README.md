# jolt-example

Pac-Man on raylib, called from [jolt](https://github.com/jolt-lang/jolt) through
[net.b12n/raylib](https://github.com/jlt-commons/raylib-jlt), which binds raylib
over `jolt.ffi`. jolt is a native Clojure on Chez Scheme, so there is no JVM
here either.

```sh
bb info                   # every task here
bb pacman                 # play it
bb pacman 10              # quit after ten seconds of game time
bb pacman 3 out.png 76    # quit after three, capture frame 76
bb check                  # compile the namespace
bb doctor                 # jolt and libraylib
```

Or through the alias directly, which is what the tasks wrap:

```sh
jolt -M:pacman
jolt -M:pacman 3 out.png 76
```

Arrows or WASD steer, ENTER restarts after GAME OVER.

## Prerequisites

`jolt` 0.8.0 or newer, and a system raylib 6.0 or newer:

```sh
brew install raylib       # macOS
```

The binding lives in the `lib/` subtree of its repo rather than at the root, so
the coordinate in `deps.edn` carries `:deps/root` alongside the git pin. You do
not have to say where `libraylib` is: `lib/deps.edn` declares `:jolt/native` for
it, jolt resolves a dependency's native candidates against the declaring root,
and a consumer inherits the lookup.

The `:jolt/min-version "0.8.0"` line is load-bearing. `jolt.ffi/write` took its
value and offset the other way round before 0.8.0, and both spellings are
integers, so an older runtime writes to the wrong place rather than failing.
Declaring the floor makes it refuse instead.

## What this port has to do differently

`jolt.ffi` moves scalars, not structs passed by value. A `Color` is packed into
one integer, and `DrawCircleSector` is unreachable because its centre is a
`Vector2` by value. The wrapper's `sector!` fills that gap by emitting an rlgl
triangle fan, and Pac-Man is one: a sweep from the far lip of his mouth round to
the near one, leaving the mouth as the wedge it never covers.

**The angle convention is the trap worth knowing.** `sector!` puts zero at the
top and increases clockwise, so right is 90 degrees. Raw raylib puts zero at the
right. The two differ by a quarter-turn, and getting it wrong points Pac-Man's
mouth sideways to his direction of travel while everything still compiles, still
runs, and still looks like a Pac-Man. This port measures the heading as
`atan2(fx, -fy)`; the jank and Clojure ports, which call raylib's own
`DrawCircleSector`, use `atan2(fy, fx)`.

## Credit

The game is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
This port descends from his
[`examples/pacman.clj`](https://github.com/babashka/ffi/blob/main/examples/pacman.clj)
in [babashka/ffi](https://github.com/babashka/ffi), by way of the `pacman`
example in [jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt),
which is itself a port of it.

babashka/ffi is MIT licensed, and the notice is reproduced in
[NOTICE.md](../NOTICE.md) at the root of this repository.
