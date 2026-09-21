# babashka-example

Pac-Man on raylib, called from [babashka](https://babashka.org) over
`babashka.ffi`. No build step and no dependencies beyond `bb` itself, so this is
the fastest of the four to get on screen.

```sh
bb info                   # every task here
bb pacman                 # play it
bb pacman 10              # quit after ten seconds of game time
bb pacman 3 out.png 76    # quit after three, capture frame 76
bb check                  # load the namespace, no window
bb doctor                 # is libraylib where the FFI can find it
```

Arrows or WASD steer, ENTER restarts after GAME OVER.

## Prerequisites

`bb`, and a system raylib 6.0 or newer:

```sh
brew install raylib       # macOS
```

`bb doctor` prints the path `babashka.ffi` actually resolved, which is the
quickest way to tell a missing library from a wrong one.

## What this port has to do differently

`babashka.ffi` passes structs by value as well as scalars, so neither of the
choices below is forced. Both are worth knowing anyway.

A raylib `Color` is four bytes. Rather than describe the struct, `rgba` packs
those same four bytes into one integer and every draw call takes a `:uint`. A
four-byte all-integer struct travels in a single register, so this is the
identical memory the ABI would have pushed, and it saves building a map per
call.

`DrawCircleSector` is reachable here, contrary to what this file used to say.
Binding it as `[:struct [[:x :float] [:y :float]]]` and passing `{:x :y}` draws
the wedge on babashka 1.13.220. The port keeps the original's approach and
builds Pac-Man one level down, as an rlgl triangle fan: `rlBegin`,
a run of `rlVertex2f` calls sweeping from the far lip of the mouth round to the
near one, then `rlEnd`. The wedge the fan never covers is the mouth. Because the
fan emits centre, rim, rim, half its triangles wind against raylib's culling
order, so the example turns backface culling off once at startup.

Everything above the drawing layer is ordinary Clojure and reads the same as the
other three ports.

## Credit

The game is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
This port began from his
[`examples/pacman.clj`](https://github.com/babashka/ffi/blob/main/examples/pacman.clj)
in [babashka/ffi](https://github.com/babashka/ffi), which already had the maze,
the ghosts and the rlgl fan. The other three ports here then came off this one.

babashka/ffi is MIT licensed, and the notice is reproduced in
[NOTICE.md](../NOTICE.md) at the root of this repository.
