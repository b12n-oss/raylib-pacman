# Notice

This repository is under the Eclipse Public License 2.0, in [LICENSE](LICENSE).
Parts of it derive from third-party work that carries its own licence, and
those notices are reproduced below.

## The game came from babashka.ffi

All four ports here descend from `examples/pacman.clj` in
[babashka/ffi](https://github.com/babashka/ffi/blob/main/examples/pacman.clj),
written by **Michiel Borkent**
([@borkdude](https://github.com/borkdude)), first committed as
[`94ccd77`](https://github.com/babashka/ffi/commit/94ccd77e7cb558efb2c61f17172ca5eb6a3ec162)
(2026-08-31).

That example is where the maze, the ghost personalities, the tile-centre
movement rule and the rlgl triangle fan all came from. The twenty-one maze rows
are byte-identical in every port here, which is the shortest way to see the
lineage. What this repository added is the other three runtimes, the drawing
layers they each need, the unattended run arguments and the project layout
around them. The game underneath is his.

The jolt port arrived by way of the `pacman` example in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt), which is
itself a port of the same original.

babashka/ffi is MIT licensed, and its notice is reproduced in full:

```
MIT License

Copyright © 2026 Michiel Borkent

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Pac-Man itself

Pac-Man is Namco's, from 1980. Nothing here is affiliated with or endorsed by
Namco, and no original art, sound or level data is used. The maze and the ghost
behaviour are a reimplementation.

## raylib

Every port links against [raylib](https://www.raylib.com), which is zlib/libpng
licensed. raylib is not bundled here, and each example loads whatever copy the
system provides.
