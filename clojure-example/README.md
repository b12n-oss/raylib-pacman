# clojure-example

Pac-Man on raylib, called from Clojure on the JVM through
[raylib-clj](https://github.com/b12n-oss/raylib-clj), which binds raylib with
[coffi](https://github.com/IGJoshua/coffi) over the JDK's Panama FFI.

```sh
bb info                   # every task here
bb pacman                 # play it
bb pacman 10              # quit after ten seconds of game time
bb pacman 3 out.png 76    # quit after three, capture frame 76
bb check                  # load the namespace, no window
bb doctor                 # JDK version and libraylib
```

Or without `bb` at all, since the aliases are the real interface:

```sh
clojure -M:pacman
clojure -M:pacman 3 out.png 76
```

Arrows or WASD steer, ENTER restarts after GAME OVER.

## Prerequisites

A JDK 22 or newer, because Panama is what coffi sits on, plus a system raylib
6.0 or newer:

```sh
brew install raylib       # macOS
```

raylib-clj arrives as a pinned git dependency, so the first run clones it.

## Three JVM flags, none of them optional

They live in the `:pacman` alias in `deps.edn` and each one fails differently
when it is missing:

- `--enable-native-access=ALL-UNNAMED`, or Panama refuses the downcall.
- `-XstartOnFirstThread`, because macOS insists GLFW's event loop runs on
  thread 0. This one is macOS-only. On Linux the JVM rejects it outright as an
  unrecognized option, so drop it there.
- `-Djava.library.path=...`, which is where `libraylib` is looked up.

## What this port gets for free

raylib-clj maps the structs to plain Clojure data, so a `Color` is
`{:r :g :b :a}` and a `Vector2` is `{:x :y}`. That makes `DrawCircleSector`
directly callable, and Pac-Man is one: a pie slice running from the far lip of
his mouth round to the near one, so the mouth is the wedge the sector leaves
out. The babashka and jolt ports have to hand-roll the same shape from triangles
because their FFI will not pass a `Vector2` by value.

One thing is worth knowing about the angle. raylib measures a sector from the
positive x axis and y grows downward, so zero points right and the angle
increases clockwise on screen. The heading is then a plain `atan2` with no
quarter-turn correction, which is not true of every raylib wrapper: the jolt one
puts zero at the top.

## Two calls this example declares itself

raylib-clj does not bind `TakeScreenshot` or `rlDrawRenderBatchActive`, and both
are needed only by the unattended screenshot path. Rather than patch the
library, the example declares them at the top of `pacman.clj` with coffi's
`defcfn`. It is four lines, and it doubles as a worked example of extending the
binding when you need a call it does not carry yet.

## Credit

The game is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
This port descends from his
[`examples/pacman.clj`](https://github.com/babashka/ffi/blob/main/examples/pacman.clj)
in [babashka/ffi](https://github.com/babashka/ffi), by way of the babashka port
next door.

babashka/ffi is MIT licensed, and the notice is reproduced in
[NOTICE.md](../NOTICE.md) at the root of this repository.
