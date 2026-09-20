# Contributing

The repository is four standalone projects plus a small root that ties them
together. Most changes touch exactly one of them.

## Before you start

Install raylib 6.0 or newer, then whichever toolchain the port you are touching
needs. [Getting started](getting-started.md) has both, and `bb doctor-all`
tells you what is missing with the command to fix it.

## The gate

```sh
bb check-all
```

That compiles or loads all four ports. It is fast for three of them and slower
for jank, which builds natively. Never commit red.

There is no unit-test suite, and that is a deliberate limit rather than an
oversight. The game is a window: there is no assertion worth writing about
whether Pac-Man looks right, and synthetic clicks do not actuate a raylib
window at all. So the second gate is a rendered frame that somebody looks at:

```sh
bb shot-all                       # a PNG from each port
bb play jank 3 out.png 76         # one port, one chosen frame
```

**Look at the image.** A wrong colour, a wrong coordinate and a sector swept
from the wrong angle all compile, run, and produce a perfectly plausible
window. Nothing but the picture catches them. [Drawing
Pac-Man](drawing-pac-man.md) has the worked example, including why one
screenshot is not enough to check the mouth.

## Changing the game

If a change affects gameplay, it affects all four ports. They carry full copies
on purpose, so the change has to be made four times and each one verified
separately. That sounds worse than it is: the game half is nearly identical
across them, and the diff is usually mechanical.

What is not mechanical is the drawing layer. Do not copy a `heading-deg`
between ports. raylib measures a sector from 0=right and the jolt wrapper from
0=up, and a wrong conversion is invisible to every gate except the picture.

## Adding a runtime

1. A new `<name>-example/` directory with its own build file and a full copy of
   the game.
2. Its own `bb.edn` carrying the same tasks as the others: `info`, `pacman`,
   `shot`, `check`, `doctor`.
3. One entry in the `examples` registry at the top of the root `bb.edn`. Every
   root task walks that vector, including the demo recorder's item list, so
   nothing else in the root needs editing.
4. A `README.md` in the directory, and a page in `docs/guide/`.

Keep the CLI contract: `[seconds] [shot.png] [frame]`, with seconds counted as
game time rather than wall time. [Running unattended](running-unattended.md)
explains why.

## Style

Match the surrounding code. A few things that are not obvious:

- **`bb.edn` is EDN.** No `#"regex"`, no `@deref`, no `#(...)`. Use
  `(re-pattern "...")`, `(deref ...)` and `(fn [x] ...)`. Run `bb tasks` after
  editing: reader-macro mistakes only fail at runtime, and they abort every
  `bb` invocation, not just the task you touched.
- **jank comments must be ASCII.** A stray em-dash trips the lexer with
  `lex/invalid-unicode`.
- **Comments explain why, not what.** The interesting content in this
  repository is the reasons, and most of them were measured rather than
  reasoned about. If you find a new one, write down what you ran.

## The docs site

```sh
bb site:build     # generate _site/
bb site:serve     # build, then serve at localhost:3000
```

It needs a [docs-engine](https://github.com/jlt-commons/docs-engine) checkout;
the task tells you where to clone it if it cannot find one. Nothing publishes.

## The demo GIFs

`bb record` re-records them, and it needs `screen-grab` and `cgevent`, which
are internal b12n tools and not public yet. You will not be able to run it, and
you do not need to: every GIF is committed, so the docs and the README gallery
build without any capture toolchain. If a change makes a committed preview
wrong, say so in the pull request and a maintainer will re-record it.
