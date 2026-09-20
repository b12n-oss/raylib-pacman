# The game

This is the half that barely changes between runtimes. Read it once and the
four ports all become the same file with a different bottom third.

## One map, threaded

There is no mutable game state. The world is a single immutable map, `step`
takes it and returns the next one, and `draw-state!` is a function of the map it
is handed. The main loop is then just:

```clojure
(loop [frame 0
       s (new-game)]
  (if (window-should-close?)
    s
    (let [s' (step s)]
      (begin-drawing)
      (draw-state! s')
      (end-drawing)
      (recur (inc frame) s'))))
```

Nothing below `step` draws and nothing below `draw-state!` decides anything.
That split is what let the same logic move across four runtimes without being
rethought each time. It also means you can reason about a frame by reading one
function.

The map holds the obvious things:

```clojure
{:pac    {:x :y :dx :dy :ndx :ndy :fx :fy :mouth}
 :ghosts [{:name :color :scatter :x :y :dx :dy :frightened :home-timer} ...]
 :dots   #{[x y] ...}
 :score :lives :level
 :mode-timer :chase? :combo
 :message :message-timer :cleared? :over? :clock}
```

`:dots` being a set rather than a grid is worth a second look. Eating is
`(disj dots [tx ty])`, the level-cleared test is `(empty? dots)`, and drawing is
one pass over whatever is left. A grid would need all three written differently
and would keep re-reading cells that are already empty.

## The maze is a vector of strings

```clojure
(def maze
  ["###################"
   "#........#........#"
   ...])
```

`#` is wall, `.` a dot, `o` a power pellet, `-` the ghost-house door, `P` where
Pac-Man starts and `G` the house itself. Everything else is derived by reading
it: `pac-start`, `door`, `house-slots` and the initial dot set all come from
`find-tile` rather than from a second table that could drift out of step with
the picture.

Two details are load-bearing. Row 10 has open ends, which is the side tunnel,
and `tile-at` wraps `x` with `mod` so walking off one edge arrives at the other.
Anything above or below the maze reads as wall, so no bounds check is needed
anywhere else.

The door gets two predicates rather than one. `wall?` blocks Pac-Man on walls
and on the door; `ghost-wall?` blocks ghosts on walls only, so a ghost can leave
its own house and Pac-Man cannot enter it.

## Movement decides only at tile centres

This is the part worth reading before the rest, because it is the part that
looks unnecessary until you remove it.

An entity carries a position in tile units, a heading, and nothing else.
`step-entity` advances it by `speed * dt`, and the one rule is that a direction
may only be chosen at the centre of a tile:

```clojure
(let [dist      (* speed dt)
      cx        (+ (floor x) 0.5)
      cy        (+ (floor y) 0.5)
      to-centre (+ (* dx (- cx x)) (* dy (- cy y)))]
  (if (and (>= to-centre -1.0e-9) (<= to-centre dist))
    ;; this step reaches the centre: ask `decide`, spend the leftover on the
    ;; new heading
    ...
    ;; otherwise just keep going
    ...))
```

If a step would carry the entity past the centre, it stops there, asks `decide`
for a new heading, and spends the remainder of the step on it. Deciding anywhere
else lets an entity turn while straddling two tiles, and the corner it turns
into is a wall. The `-1.0e-9` is there because floating point lands on the
centre approximately rather than exactly.

`decide` is where Pac-Man and the ghosts differ, and it is the only place they
differ. Pac-Man's prefers the buffered turn:

```clojure
(cond
  (and (turn-buffered?) (not (wall? (+ tx ndx) (+ ty ndy)))) [ndx ndy]
  (not (wall? (+ tx dx) (+ ty dy)))                          [dx dy]
  :else                                                      [0 0])
```

Which is what makes the controls feel right. Pressing left a few tiles before
the junction turns at the junction, rather than being dropped because you were
early. The keypress sets `:ndx`/`:ndy` and `step-entity` applies it at the first
centre where it is legal.

`:fx`/`:fy` track the last non-zero heading separately from the current one, so
a stopped Pac-Man keeps facing the way he was going instead of snapping to a
default.

## The ghosts

Their personalities are the whole reason the game has a character, and they are
four lines of arithmetic:

- **Blinky** targets Pac-Man's tile. Straight pursuit.
- **Pinky** targets four tiles ahead of him, which cuts him off rather than
  following him.
- **Inky** takes the point two tiles ahead of Pac-Man and reflects Blinky's tile
  through it. This is why he appears to change his mind halfway down a corridor:
  his target depends on where Blinky is.
- **Clyde** chases while he is more than eight tiles away and breaks for his
  scatter corner once he is closer. He is the one who lets you off.

At a tile centre a ghost takes whichever legal direction ends up closest to its
target by squared distance, and reversing is forbidden:

```clojure
(for [[ndx ndy] [[0 -1] [-1 0] [0 1] [1 0]]
      :when (and (not (reverse-of? ndx ndy dx dy))
                 (not (ghost-wall? (+ tx ndx) (+ ty ndy))))]
  [ndx ndy])
```

No reversal is what makes a ghost commit to a route instead of oscillating in a
corridor. A dead end is the one case with no option left, and there the
reversal is put back so the ghost can get out.

Scatter and chase alternate on a timer, twenty seconds and seven. A power
pellet sets `:frightened` on all four, which swaps the target for a random pick
and drops their speed. Eating them in one pellet scores 200, 400, 800, 1600,
which is `(* 200 (bit-shift-left 1 (dec combo)))`.

## dt is clamped

```clojure
(min 0.05 (get-frame-time))
```

One line, and it is the difference between a stall being a hiccup and a stall
being a bug. Without it a frame that takes half a second moves Pac-Man several
tiles in a single step, straight through whatever was in the way, because the
wall test only runs at tile centres and he skipped them all.

It also turns out to matter for the deadline. See
[running-unattended.md](running-unattended.md).
