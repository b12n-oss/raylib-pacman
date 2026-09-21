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

`babashka.ffi` moves scalars across the boundary, not structs. Two consequences
run through the whole file.

A raylib `Color` is four bytes. Rather than pass the struct, `rgba` packs those
same four bytes into one integer and every draw call takes a `:uint`. This is
not a trick so much as the identical memory the ABI would have pushed anyway.

`DrawCircleSector` is out of reach, because its centre is a `Vector2` by value.
Pac-Man is therefore built one level down, as an rlgl triangle fan: `rlBegin`,
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
