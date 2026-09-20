(defproject raylib-pacman/jank-example "0.1-SNAPSHOT"
  :license {:name "zlib"
            :url "https://opensource.org/license/zlib"}
  :dependencies [[org.jank-lang.commons/raylib-sys "2026.09-3"]]
  :plugins [[org.jank-lang/lein-jank "2026.09-7"]]
  :middleware [leiningen.jank/middleware]
  :main raylib-pacman.pacman
  :profiles {:base {:jank {:target-dir "target/debug"
                           :optimization-level 0}}
             :release {:jank {:target-dir "target/release"
                              :optimization-level 3}}})
