# babashka

`babashka-example/` calls raylib from [babashka](https://babashka.org) over
`babashka.ffi`. Of the four this is the one with no build step at all: a
`bb.edn`, a source file, and `bb pacman` opens a window.

## Declaring the surface

```clojure
(require '[babashka.ffi :as ffi :refer [defcfn]])
(ffi/load-system-library "raylib")

(defcfn draw-circle "DrawCircle" [:int :int :float :uint] :void)
(defcfn measure-text "MeasureText" [:string :int] :int)
(defcfn key-down?   "IsKeyDown"   [:int] :uint8)
```

Twenty or so of these cover the whole game. `load-system-library` does the
lookup, and `bb doctor` prints what it resolved, which is the quickest way to
tell a missing raylib from a wrong one:

```
libraylib  /opt/homebrew/lib/libraylib.dylib
```

## Scalars only

Every parameter above is a scalar, and that is the constraint the port is
written around.

A raylib `Color` is a four-byte struct by value. Rather than pass the struct,
`rgba` packs those same four bytes into one integer:

```clojure
(defn rgba [r g b a]
  (bit-or r (bit-shift-left g 8) (bit-shift-left b 16) (bit-shift-left a 24)))
```

Note the order. This packing is the `Color` struct's own layout in memory, `r`
first at the lowest byte, which is what the ABI would have pushed. It is not the
same as raylib's `GetColor`, which reads `0xRRGGBBAA` from the other end. The
jank port uses that one instead, and the two look similar enough to swap by
accident and produce a picture in the wrong colours.

A C `bool` comes back as 0 or 1 rather than `false`/`true`, so the input
predicates wrap it:

```clojure
(defn down? [k] (pos? (key-down? k)))
```

## Pac-Man is a triangle fan

`DrawCircleSector` takes a `Vector2` centre by value, which this FFI will not
pass, so Pac-Man is emitted through rlgl instead: `rlBegin`, a run of
`rlVertex2f` sweeping from the far lip of the mouth round to the near one, then
`rlEnd`. [drawing-pac-man.md](drawing-pac-man.md) has the detail, including why
the example calls `rlDisableBackfaceCulling` once at startup.

## What it is good at

Startup. There is no compile and no JVM, so the edit-run loop is about as fast
as this gets, which makes it the best of the four for trying something out.

The cost is that the game logic runs on babashka's interpreter rather than
compiled code. At this size that is invisible, since the work per frame is a few
hundred map updates and the frame is capped at 60 FPS anyway. A heavier
simulation would notice.

## Credit

This port began from the
[`babashka/ffi` pacman example](https://github.com/babashka/ffi/blob/main/examples/pacman.clj),
which already had the maze, the ghost personalities and the rlgl fan. The
version here restructures it into a namespace with a pure `step`, to match the
shape of the other three, and adds the unattended run arguments.
