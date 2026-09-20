# Drawing Pac-Man

Pac-Man is a circle with a wedge missing. It is the only genuinely interesting
shape in the game, and it is drawn two different ways here for a reason that has
nothing to do with taste.

## The trick: sweep the body, not the mouth

The mouth is not drawn. A filled sector is swept from the far lip of the mouth
all the way round to the near lip, and the gap it never covers is the mouth:

```clojure
(let [open (deg (* 0.42 (+ 1.0 (sin mouth))))   ; half-angle, oscillating
      from (+ (heading-deg fx fy) open)]
  (sector! cx cy r from (+ from (- 360.0 (* 2.0 open))) 28 PELLET))
```

`:mouth` advances by `dt * 9.0` only while Pac-Man is moving, so he closes his
mouth when he stops. `open` runs between 0 and about 48 degrees, so the sweep
runs between a full circle and roughly 264 degrees.

The heading comes from `:fx`/`:fy`, the last *non-zero* direction, rather than
from the current one. A stopped Pac-Man keeps facing where he was going instead
of snapping back to a default.

## Two implementations, because two FFIs cannot make the call

`DrawCircleSector` takes its centre as a `Vector2` by value.

**Clojure and jank call it.** coffi describes the struct, so raylib-clj takes
`{:x :y}`. jank has the real header, so `(cpp/Vector2 (cpp/float x)
(cpp/float y))` built inline at the argument position is a legal `Vector2`.

**babashka and jolt cannot.** Both FFIs move scalars, so both drop one level and
emit the same shape by hand as an rlgl triangle fan:

```clojure
(rl-begin RL-TRIANGLES)
(rl-color-4ub 255 224 40 255)
(dotimes [i steps]
  (let [a0 (+ base open (* sweep (/ (double i) steps)))
        a1 (+ base open (* sweep (/ (+ i 1.0) steps)))]
    (rl-vertex-2f cx cy)
    (rl-vertex-2f (+ cx (* r (cos a0))) (+ cy (* r (sin a0))))
    (rl-vertex-2f (+ cx (* r (cos a1))) (+ cy (* r (sin a1))))))
(rl-end)
```

Which is, near enough, what `DrawCircleSector` does internally. rlgl is
raylib's own immediate-mode layer and takes scalars throughout, so it is the
escape hatch whenever a by-value struct is in the way.

One consequence is easy to miss. The fan emits centre, rim, rim, so half its
triangles wind the opposite way to raylib's front-facing order and get culled.
The babashka port calls `rlDisableBackfaceCulling` once at startup; the jolt
wrapper's `sector!` handles it by emitting rim, centre, rim instead. Either
fixes it. Neither is obvious from a blank screen.

## The angle convention, which breaks silently

This is the part worth slowing down for.

**raylib** measures a sector from the positive x axis, and screen y grows
downward. So zero points **right** and the angle increases **clockwise** on
screen. The heading is then a plain `atan2` in degrees:

```clojure
(defn heading-deg [fx fy]
  (deg (atan2 fy fx)))        ;; jank and Clojure ports
```

**The jolt wrapper's `sector!`** puts zero at the **top** instead, with the rim
at `(sin t, -cos t)`, still clockwise. Right is 90 degrees there. So that port
converts:

```clojure
(defn heading-deg [fx fy]
  (Math/toDegrees (Math/atan2 (double fx) (double (- fy)))))   ;; jolt port
```

**The babashka port** works in radians directly against `cos`/`sin`, which is
the raylib convention again, so it uses `atan2(fy, fx)` with no conversion.

Getting this wrong costs nothing at compile time and nothing at run time. The
program starts, the window opens, Pac-Man appears, he moves correctly, he eats
dots correctly, and his mouth points a quarter-turn away from the direction he
is walking. There is no error to read. The only way to catch it is to look at a
frame.

## Verifying it

The mouth oscillates with a period of about 0.7 seconds, or 42 frames at 60 FPS,
so a single screenshot can easily land on a closed mouth and tell you nothing.
That happened while building this repo: the Clojure port's first capture showed
a featureless yellow circle.

The fix is to capture several frames across one period and read them together.
Every example takes a frame number for exactly this:

```sh
cd clojure-example
for f in 66 76 86; do bb pacman 3 "mouth-$f.png" "$f"; done
```

At frame 66 he was a closed circle, at 76 the mouth was open and pointing left,
and at 86 it was wide open and still pointing left. He starts moving left, so
left is correct, and three frames prove what one could not.
