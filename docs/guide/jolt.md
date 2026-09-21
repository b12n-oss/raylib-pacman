# jolt

`jolt-example/` calls raylib from [jolt](https://github.com/jolt-lang/jolt), a
native Clojure on Chez Scheme, through
[net.b12n/raylib](https://github.com/jlt-commons/raylib-jlt) over `jolt.ffi`.

It is the only port of the four that uses a purpose-built wrapper library rather
than talking to raylib itself, so the game code is the shortest and the most
Clojure-looking:

```clojure
(rl/rect!   {:x sx :y sy :width CELL :height CELL :color WALL-EDGE})
(rl/circle! {:x cx :y cy :radius 7.0 :color PELLET})
(rl/text!   "SCORE 120" {:x 20 :y 20 :size 28 :color LABEL})
```

## The dependency, and two lines that matter

```clojure
{:jolt/min-version "0.8.0"
 :deps {net.b12n/raylib {:git/url "https://github.com/jlt-commons/raylib-jlt"
                         :git/sha "4a2c43682b9acc310c3fd97f27c220eea5e1b176"
                         :deps/root "lib"}}}
```

`:deps/root "lib"` is there because the binding lives in a subtree of its repo
rather than at the root. Leave it off and you get the example suite instead of
the library.

`:jolt/min-version "0.8.0"` is a real guard, not boilerplate. `jolt.ffi/write`
took its value and offset the other way round before 0.8.0, and both spellings
are integers, so an older runtime writes to the wrong place rather than
failing. Declaring the floor makes it refuse instead of misbehaving.

## The native lookup comes with the dependency

Nothing in this example says where `libraylib` is. The binding's own
`lib/deps.edn` declares it:

```clojure
:jolt/native [{:name "raylib"
               :darwin ["/opt/homebrew/lib/libraylib.dylib" "libraylib.dylib"]
               :linux  ["libraylib.so.6" "libraylib.so"]}]
```

jolt resolves a dependency's native candidates against the *declaring* root, so
a consumer inherits the lookup rather than restating it. This is the tidiest of
the four on that point. The Clojure port needs `-Djava.library.path` in its
launcher, and babashka resolves by name at runtime.

## Packed colours, and a fan that is a choice

A `Color` is packed into an integer here, the same as in the babashka port, and
for the same good reason: a four-byte all-integer struct rides in one register,
so the packed `:uint` is the bytes the ABI wanted anyway.

`DrawCircleSector` is a different matter. This page used to call it unreachable
because its centre is a `Vector2` by value. `jolt.ffi` does pass structs by
value, with `[:by-value [:struct ...]]` on the parameter and a pointer to an
allocated layout buffer as the argument, and the call renders correctly on jolt
0.8.10, checked on 2026-09-21. The wrapper's own `sector!` is an rlgl triangle
fan, the game calls that, and it is a choice inherited from the original rather
than a gap being filled.

The wrapper also takes keyword-style arguments throughout, which is why the draw
calls above read as maps rather than positional argument lists. That is a choice
of the binding, not something jolt requires.

## The angle convention is the trap

`sector!` puts zero at the **top** and increases clockwise, with the rim at
`(sin t, -cos t)`. Raw raylib puts zero at the **right**. The two differ by a
quarter-turn.

So this port computes the heading as:

```clojure
(Math/toDegrees (Math/atan2 (double fx) (double (- fy))))
```

while the jank and Clojure ports, which call raylib's own `DrawCircleSector`,
use `atan2(fy, fx)`.

Copying a `heading-deg` from one port to another therefore compiles, runs,
draws a Pac-Man, and points his mouth sideways to his direction of travel with
no error anywhere. It is the single most transferable warning in this repo, and
[drawing-pac-man.md](drawing-pac-man.md) covers how to actually check it.

## What it is good at

Native startup with no warm-up, a Clojure-shaped API, and the native library
lookup handled by the dependency. Among the three non-JVM ports it is the one
that reads most like ordinary Clojure, because someone already did the work of
wrapping raylib properly.

The cost is that you are reading the wrapper's conventions as well as raylib's,
and where the two disagree, as with the sector angle, the wrapper wins silently.

## Credit

This port is a close copy of the `pacman` example in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt), with the
repo's own smoke harness swapped for the standalone deadline and screenshot
arguments the other three use.

That example is itself a port, so the chain runs one step further back. The
game originated as
[`examples/pacman.clj`](https://github.com/babashka/ffi/blob/main/examples/pacman.clj)
in [babashka/ffi](https://github.com/babashka/ffi), by **Michiel Borkent**
([@borkdude](https://github.com/borkdude)), and the twenty-one maze rows here
are still byte-identical to his.
