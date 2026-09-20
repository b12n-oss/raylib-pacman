# Clojure on the JVM

`clojure-example/` calls raylib through
[raylib-clj](https://github.com/b12n-oss/raylib-clj), which binds it with
[coffi](https://github.com/IGJoshua/coffi) over the JDK's Panama FFI. This is
the only one of the four with a real JVM under it, and the only one that gets
raylib's structs handed to it as ordinary Clojure data.

## The dependency

```clojure
{:deps {io.github.b12n-oss/raylib-clj
        {:git/url "https://github.com/b12n-oss/raylib-clj"
         :git/sha "5404ec049ff2d036fcf219d309b2b5a09706cfb5"}}}
```

coffi, insn and Clojure arrive transitively. `libraylib` does not: it is a
system library, found through `java.library.path` at load time.

## Three JVM flags, none optional

They live in the `:pacman` alias and each fails differently when missing:

- `--enable-native-access=ALL-UNNAMED`. Panama refuses the downcall without it.
- `-XstartOnFirstThread`. macOS requires GLFW's event loop on thread 0. This one
  is macOS-only, and on Linux the JVM rejects it as an unrecognized option, so
  drop it there.
- `-Djava.library.path=...`. Where `libraylib` is looked up.

This is the only port whose *launcher* needs configuring. The other three either
have no JVM or wrap the native lookup in the dependency itself.

## Structs as data

```clojure
(rsb/draw-rectangle! x y w h {:r 33 :g 33 :b 222 :a 255})
(rsb/draw-circle-sector! {:x 120.0 :y 140.0} 30.0 40.0 320.0 28 PELLET)
```

A `Color` is `{:r :g :b :a}` and a `Vector2` is `{:x :y}`. coffi describes the
layout once in the binding and marshals it at the call, so the palette in this
port is plain data:

```clojure
(defn rgba [r g b a] {:r r :g g :b b :a a})
```

Compare that with the babashka and jolt ports, which pack the same four bytes
into an integer because their FFI moves scalars only. The Clojure port never
has to think about byte order, and it is also the only one that can call
`DrawCircleSector` without a second thought.

The cost is at the call: coffi wants the declared primitive, so a `::mem/float`
parameter gets `(float ...)` and a `::mem/int` gets `(int ...)`. Passing a
double where a float is declared is the usual first mistake.

## Extending the binding in the example

raylib-clj does not bind `TakeScreenshot` or `rlDrawRenderBatchActive`, and both
are only needed by the unattended screenshot path. Rather than patch the
library, the example declares them itself:

```clojure
(defcfn take-screenshot!
  {:arglists '([file-name])}
  "TakeScreenshot"
  [::mem/c-string] ::mem/void)

(defcfn draw-render-batch-active!
  "rlDrawRenderBatchActive"
  [] ::mem/void)
```

Four lines, no fork, and it doubles as a worked example of reaching a call the
binding does not carry yet. This is a genuine advantage of the describe-the-C
approach: adding a function is a data declaration, not a build.

## What it is good at

Everything around the game. This is the only port with the JVM's tooling
attached, so a REPL, a profiler and the rest of the ecosystem are all available
while it runs. If the game were going to grow into something with save files,
networking or a content pipeline, this is the one that would carry it.

The cost is startup, which is JVM startup plus a git dependency to resolve on
first run, and a JDK 22 floor that the other three do not have.
