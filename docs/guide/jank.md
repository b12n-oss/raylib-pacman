# jank

`jank-example/` calls raylib from [jank](https://jank-lang.org), a native
Clojure dialect on LLVM. There is no JVM and there is no binding layer either:
the namespace includes `raylib.h` and calls the C functions directly.

```clojure
(ns raylib-pacman.pacman
  (:include "raylib.h" "rlgl.h" "math.h"))

(cpp/DrawCircle (cpp/int x) (cpp/int y) (cpp/float r) colour)
```

That is the deepest difference between this port and the other three. Nothing
describes `DrawCircle` to the runtime, because the real declaration is the one
the C++ compiler already has from the header.

**Everything below was measured against jank 0.1-alpha with `raylib-sys
2026.09-3` in September 2026.** jank moves quickly and is explicitly alpha, so
treat each of these as a finding with a date rather than a permanent property.

## A jank fn may not return a native value

```
returning a native object of type 'Color', which is not convertible to a
jank runtime object
```

A native bound to a `let`-local is fine. One constructed inline at an argument
position is fine. The return position is not, and neither is carrying one
through `loop`/`recur`.

So colours travel as packed jank integers and the `Color` is rebuilt at each
call:

```clojure
(defn rgba [r g b a]
  (bit-or (bit-shift-left r 24) (bit-shift-left g 16) (bit-shift-left b 8) a))

(defn circle! [x y r c]
  (cpp/DrawCircle (cpp/int (int x)) (cpp/int (int y))
                  (cpp/float (+ 0.0 r))
                  (cpp/GetColor (cpp/uint32_t c)))
  nil)
```

Note the packing is `0xRRGGBBAA`, because that is what raylib's `GetColor`
reads. The babashka and jolt ports pack the other way round, matching the
struct's own byte order. The two are easy to confuse and the symptom is a
picture in the wrong colours rather than an error.

Four wrappers like `circle!` cover the whole game, which keeps the constraint in
one place instead of forty.

## `case` does not compile with integer arms

This one cost the most time, so it is worth stating plainly:

```clojure
(case :pinky :blinky 1 :pinky 2 3)
```

fails the build with `no viable overloaded '='`, raised from
`clojure/core.jank:4551`, under roughly four hundred lines of C++ template
diagnostics about `oref` assignment. The error names neither your file, nor your
form, nor the word `case`.

Every dispatch in this port is a `cond` as a result. The other three use `case`
in the same places and are fine.

The transferable lesson: when a jank build fails inside `clojure/core.jank`
rather than inside your own file, something in your code expanded into a shape
core could not type. Bisecting your own source is the only way to locate it. A
throwaway probe namespace with one construct per line finds it in a single
compile, which is how this was found, and it is cheaper written before the port
than after.

## Numbers

`mod` and `rem` return reals; `quot` does not:

```clojure
(mod 7 3)   ;; => 1.0
(quot 7 3)  ;; => 2
```

Handing `1.0` to a C `int` throws `invalid object type (expected integer found
small_real)`. The side tunnel wraps the column with `mod`, so `tile-at` narrows
before indexing:

```clojure
(nth (nth maze y) (int (mod x MW)))
```

`cpp/float` wants a real, so `(cpp/float (+ 0.0 n))` is the idiom for a value
that might be an integer. There is a subtlety under that: `(+ 0.0 ...)` only
boxes when something in the chain is already a jank object, and an all-native
chain stays an unboxed `f64` and fails codegen instead.

## No JVM

No `Math/*`, no `format`, no `parse-long`, no `System/currentTimeMillis`. C
maths comes through `math.h` as `cpp/sin`, `cpp/atan2`, `cpp/floor` and
`cpp/fabs`. `str` replaces `format`. `cpp/TextToInteger` stands in for
`parse-long`, and answers 0 for anything unparseable, which happens to be the
"no deadline" value anyway.

Comments must be ASCII. A stray em-dash trips the lexer with
`lex/invalid-unicode`.

## The warm-up belongs to `lein run`, not to jank

Under `lein run`, frame 0 pays for the whole draw path at once. Measured here:
about 6.3 seconds for the first twenty frames, then a steady 0.34 seconds per
twenty, which is 60 FPS exactly. Six game-seconds of play took 19.6 seconds of
wall clock.

The tempting conclusion is that this is what jank costs. It is not. The same
code, run as the AOT binary `lein` leaves in `target/debug/`, reaches a window
in about a second and plays six game-seconds in 6.4 of wall clock. No warm-up
is measurable at all:

```
lein run --disable-sandbox 6     6 game-seconds took 19.6s wall
./target/debug/jank-example 6    6 game-seconds took  6.4s wall
```

So `lein run` is the convenient dev path and the slow one, and the binary it
produces is the honest measure of the language. The recorded preview uses the
binary for exactly this reason.

It still broke the deadline, which is why every port counts game time rather
than wall time. See [running-unattended.md](running-unattended.md).

## Building it

The project layout follows the
[raylib-sys example](https://github.com/jank-lang/commons/tree/main/raylib-sys/example)
in jank's commons repo: `project.clj` with `lein-jank` as plugin and middleware,
and debug and release profiles differing only in optimization level.

**`--disable-sandbox` is required on macOS.** There is no `bwrap`, so the native
build must run unsandboxed. Without it the build aborts with `No 'bwrap'
executable found`, which is not a jank error and never mentions jank. Every
`lein` call in this example's `bb.edn` carries the flag.

The first run compiles `raylib-sys` natively and takes a few minutes.

## Credit

The game is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
This port descends from
[`examples/pacman.clj`](https://github.com/babashka/ffi/blob/main/examples/pacman.clj)
in [babashka/ffi](https://github.com/babashka/ffi), by way of the babashka port
in this repo. What changed on the way over is the drawing layer, plus the
workarounds the page above describes.

babashka/ffi is MIT licensed, and the notice lives in `NOTICE.md` at the root
of the repository.
