# Demos

One animated preview per port, all four running the same game. Every GIF here is committed, so nothing in this repo needs a capture toolchain to build. They were recorded with `screen-grab` over `cgevent`, internal b12n tools that are not public yet; `bb record` regenerates them on a machine that has both.

## The four ports

### babashka-example

raylib over babashka.ffi. Colours packed into a uint and Pac-Man emitted as an rlgl triangle fan, because this FFI moves scalars rather than structs.

![babashka-example](babashka-example.gif)

### clojure-example

raylib over raylib-clj, which binds it with coffi on Panama. Structs arrive as plain maps, so DrawCircleSector is called directly.

![clojure-example](clojure-example.gif)

### jank-example

raylib over jank's cpp/ interop, including raylib.h directly. Recorded from the AOT binary, which starts in about a second; `lein run` compiles as it loads and takes roughly 13 seconds longer to reach the first frame.

![jank-example](jank-example.gif)

### jolt-example

raylib over net.b12n/raylib on jolt.ffi. The wrapper's sector! measures from 0=up, where raylib measures from 0=right.

![jolt-example](jolt-example.gif)

