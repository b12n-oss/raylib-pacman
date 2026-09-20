# Crossing to C

raylib is a C library. All four ports call the same functions in the same order
and get the same picture, so every difference between them is a difference in
how their runtime gets an argument across the boundary. This page is the
comparison the repo exists for.

## The four mechanisms

| | Mechanism | Declared as | Built at |
|---|---|---|---|
| babashka | `babashka.ffi`, on Panama inside the GraalVM image | `(defcfn draw-circle "DrawCircle" [:int :int :float :uint] :void)` | runtime, no build step |
| Clojure | coffi, on the JDK's Panama | `(defcfn draw-circle! "DrawCircle" [::mem/int ::mem/int ::mem/float ::rs/color] ::mem/void)` | runtime, needs JDK 22+ |
| jank | C++ interop, header included into the compilation | `(cpp/DrawCircle x y r c)` after `(:include "raylib.h")` | compile time, native |
| jolt | `jolt.ffi`, on Chez's FFI | wrapped once in `net.b12n/raylib` | runtime, native |

Two of these describe the C function to the runtime. jank does not describe
anything: it includes the real header and the real declaration is the one the
C++ compiler already has. That is the deepest difference on this page, and most
of the others fall out of it.

## What each one will not carry

Every FFI here moves scalars. They diverge on aggregates, and raylib is a
library whose API is full of small structs passed by value.

**`Color` is four bytes.** raylib takes it by value in every draw call.

- coffi describes the struct, so raylib-clj hands it over as `{:r :g :b :a}` and
  you never think about it.
- `babashka.ffi` and `jolt.ffi` move scalars, so both pack the same four bytes
  into one integer and pass a `:uint`. This is not a trick. It is the identical
  memory the ABI would have pushed, spelled differently.
- jank can pass one, because the C++ compiler knows the type. What it cannot do
  is let a jank fn *return* one.

**`Vector2` is eight bytes**, and `DrawCircleSector` takes its centre by value.
Here babashka and jolt simply cannot make the call, which forces a different
implementation of Pac-Man himself. That is [its own
page](drawing-pac-man.md).

## The constraint that shapes the jank port

A jank fn may not return a native value:

```
returning a native object of type 'Color', which is not convertible to a
jank runtime object
```

A native value bound to a `let`-local is fine, and one constructed inline at an
argument position is fine. So this works:

```clojure
(cpp/DrawCircle x y r (cpp/GetColor (cpp/uint32_t c)))
```

and a `(defn colour-for [g] ...)` returning a `Color` does not. The port
therefore keeps colours as packed jank integers, exactly as babashka and jolt
do, and rebuilds the `Color` at each call site. Four small wrappers at the top
of the file (`rect!`, `circle!`, `sector!`, `text!`) make that one place rather
than forty.

It is worth being precise about the rule, because the obvious summary of it is
wrong. The problem is not that natives cannot be near jank values. It is the
*return* position specifically, plus carrying one in `loop`/`recur` state.

## Numbers are not interchangeable either

Each runtime has its own way of getting a Clojure number into a C parameter of a
given width, and each one fails differently when you get it wrong.

**jank** is the strictest, and two of its rules cost real time here.

`mod` and `rem` return reals; `quot` does not. Measured:

```clojure
(mod 7 3)   ;; => 1.0
(quot 7 3)  ;; => 2
```

Handing that `1.0` to a C `int` throws `invalid object type (expected integer
found small_real)`. In this game the column wraps with `mod` for the side
tunnel, so `tile-at` narrows with `int` before indexing:

```clojure
(nth (nth maze y) (int (mod x MW)))
```

And `cpp/float` wants a real, so an integer straight out of a C call has to be
widened first: `(cpp/float (+ 0.0 n))`. There is a subtlety underneath that.
`(+ 0.0 ...)` only boxes when something in the chain is already a jank object.
An all-native chain stays an unboxed `f64` and fails codegen instead.

**coffi** wants the exact primitive, so the Clojure port casts at the call:
`(float 7.0)` for a `::mem/float`, `(int ...)` for a `::mem/int`. Passing a
double where a float is declared is the usual first mistake.

**babashka and jolt** are the most forgiving of the four. Declare `:float` and
pass a double.

## The one that bit hardest

jank's `case` does not compile when its result arms are integers:

```clojure
(case :pinky :blinky 1 :pinky 2 3)
```

fails with `no viable overloaded '='` raised from inside
`clojure/core.jank:4551`, naming neither your form nor your file nor the word
`case`. The build output is about four hundred lines of C++ template diagnostics
about `oref` assignment.

Every dispatch in the jank port is a `cond` for this reason, including the one
choosing each ghost's target. The other three ports use `case` there and are
fine.

The general lesson is more useful than the specific bug. When a jank build fails
inside `clojure/core.jank` rather than inside your own file, the cause is a
construct in your code that expanded into something core could not type, and
bisecting your own file is the only way to find it. A probe namespace with one
construct per line costs one compile and locates it immediately. That is how
this one was found, and the probe is worth writing before the port rather than
after.

## What none of them protect you from

All four are equally happy to draw the wrong thing. A wrong argument *order*, a
colour with its bytes the wrong way round, or a sector swept from the wrong
angle all typecheck, run, and produce a window. The only gate that catches them
is a rendered frame somebody looks at, which is why every example here can
capture a PNG without a person at the keyboard. See
[running-unattended.md](running-unattended.md).
