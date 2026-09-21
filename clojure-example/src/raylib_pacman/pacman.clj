;; Pac-Man, after Michiel Borkent's (@borkdude) examples/pacman.clj in
;; babashka/ffi: https://github.com/babashka/ffi/blob/main/examples/pacman.clj
;;
;; That example is the original. The maze, the ghost personalities and the
;; tile-centre movement rule are all his, and this file is a port of it onto raylib-clj,
;; where coffi carries the structs that example had to pack by hand.
;; babashka/ffi is MIT licensed, Copyright (c) 2026 Michiel Borkent; see
;; NOTICE.md at the root of this repository for the notice in full.

(ns raylib-pacman.pacman
  "Pac-Man on raylib, called from Clojure over raylib-clj (coffi / Panama).

  Run it with `clojure -M:pacman`, or `bb pacman`. Arrows or WASD steer, ENTER
  restarts after GAME OVER.

  The ghosts keep the personalities the 1980 original gave them, which is where
  the game's character comes from. Blinky heads for Pac-Man's tile. Pinky aims
  four tiles AHEAD of him, to cut him off. Inky reflects Blinky's tile through a
  point two tiles ahead of Pac-Man, which is why he seems to change his mind.
  Clyde chases until he is within eight tiles, then breaks for his corner. They
  alternate scatter and chase on a timer, and turn blue while a power pellet is
  in effect.

  The whole world is one immutable map threaded through the loop. `step` reads
  input and returns the next state, and nothing under it draws. Movement is the
  part worth reading: a turn is BUFFERED on the keypress and applied at the next
  tile centre where it is legal, because deciding a direction anywhere but a
  centre is what lets an entity drift into a wall.

  What is specific to this port: raylib-clj hands structs across as ordinary
  Clojure maps, so a Color is {:r :g :b :a} and a Vector2 is {:x :y}. That makes
  DrawCircleSector directly callable, and Pac-Man is one of them: a pie slice
  running from the far lip of his mouth round to the near one, so the mouth is
  the wedge the sector leaves out."
  (:require
   [coffi.ffi :refer [defcfn]]
   [coffi.mem :as mem]
   [raylib.core.drawing :as rcd]
   [raylib.core.keyboard :as rck]
   [raylib.core.timing :as rct]
   [raylib.core.window :as rcw]
   [raylib.enums :as enums]
   [raylib.shapes.basic :as rsb]
   [raylib.text.drawing :as rtd])
  (:gen-class))

;; --- two calls raylib-clj does not bind yet ----------------------------------
;; Both are only needed by the unattended screenshot path. Declaring them here
;; rather than patching the library keeps this example self-contained, and it
;; doubles as a worked example of extending the binding: coffi's `defcfn` takes
;; the C symbol, the argument types and the return type, and nothing else.

(defcfn take-screenshot!
  "Take a screenshot of the current screen (filename extension defines format)"
  {:arglists '([file-name])}
  "TakeScreenshot"
  [::mem/c-string] ::mem/void)

(defcfn draw-render-batch-active!
  "Update and draw internal render batch"
  "rlDrawRenderBatchActive"
  [] ::mem/void)

;; --- the maze ----------------------------------------------------------------
;; # wall, . dot, o power pellet, - ghost-house door, P pac-man start,
;; G ghost start. Row 10 has open ends: those are the side tunnels.

(def maze
  ["###################"
   "#........#........#"
   "#o##.###.#.###.##o#"
   "#.................#"
   "#.##.#.#####.#.##.#"
   "#....#...#...#....#"
   "####.###.#.###.####"
   "#....#.......#....#"
   "#.##.#.##-##.#.##.#"
   "#.##...#GGG#...##.#"
   ".....#.#GGG#.#....."
   "#.##...#####...##.#"
   "#.##.#...#...#.##.#"
   "#....#.#####.#....#"
   "####.#...#...#.####"
   "#o.......P.......o#"
   "#.###.#######.###.#"
   "#...#....#....#...#"
   "###.#.##.#.##.#.###"
   "#.................#"
   "###################"])

(def MW (count (first maze)))
(def MH (count maze))

;; --- screen geometry ---------------------------------------------------------

(def ^:const CELL 30)
(def ^:const OX 20)
(def ^:const OY 70)
(def W (+ (* MW CELL) (* 2 OX)))
(def H (+ (* MH CELL) OY 30))

(defn px [gx] (+ OX (* gx CELL)))
(defn py [gy] (+ OY (* gy CELL)))

;; --- palette -----------------------------------------------------------------
;; raylib-clj represents a Color as a plain map, so a palette is plain data.

(defn rgba [r g b a] {:r r :g g :b b :a a})

(def BACKGROUND (rgba 6 6 14 255))
(def WALL (rgba 33 33 222 255))
(def WALL-EDGE (rgba 20 20 130 255))
(def PELLET (rgba 255 224 40 255))
(def LABEL (rgba 240 240 245 255))
(def DOOR (rgba 255 184 174 255))
(def PUPIL (rgba 20 20 60 255))
(def SCARED (rgba 40 60 230 255))
(def OVER (rgba 255 90 80 255))

;; --- reading the maze --------------------------------------------------------

(defn tile-at
  "The maze character at x,y. x wraps, which is the side tunnel; anything above
  or below the maze reads as wall."
  [x y]
  (if (or (< y 0) (>= y MH))
    \#
    (nth (nth maze y) (mod x MW))))

(defn wall?
  "Blocked for Pac-Man: walls and the ghost-house door."
  [x y]
  (let [c (tile-at x y)]
    (or (= \# c) (= \- c))))

(defn ghost-wall?
  "Blocked for a ghost: walls only, so a ghost can pass its own door."
  [x y]
  (= \# (tile-at x y)))

(defn find-tile
  [ch]
  (first (for [y (range MH)
               x (range MW)
               :when (= ch (tile-at x y))]
           [x y])))

(def pac-start (or (find-tile \P) [9 15]))
(def door (or (find-tile \-) [9 8]))
;; The tile just outside the door: where a ghost heads for on its way out.
(def door-exit [(first door) (dec (second door))])

(def house-tiles
  (vec (for [y (range MH)
             x (range MW)
             :when (= \G (tile-at x y))]
         [x y])))

;; The top row of the house, one slot per ghost.
(def house-slots
  (vec (filter #(= (second (first house-tiles)) (second %)) house-tiles)))

(defn initial-dots
  []
  (into #{} (for [y (range MH)
                  x (range MW)
                  :when (#{\. \o} (tile-at x y))]
              [x y])))

(defn centre-of [[x y]] [(+ 0.5 x) (+ 0.5 y)])

;; --- state -------------------------------------------------------------------

(def ghost-specs
  [[:blinky (rgba 255 60 50 255) [(dec MW) 0] :door-exit 0.0]
   [:pinky (rgba 255 160 200 255) [0 0] 0 1.5]
   [:inky (rgba 90 220 240 255) [(dec MW) (dec MH)] 1 3.5]
   [:clyde (rgba 255 170 60 255) [0 (dec MH)] 2 5.5]])

(defn initial-ghosts
  []
  (mapv (fn [[nm color scatter slot delay]]
          (let [[sx sy] (centre-of (if (= :door-exit slot)
                                     door-exit
                                     (nth house-slots slot)))]
            {:name nm
             :color color
             :scatter scatter
             :x sx
             :y sy
             :dx 0
             :dy -1
             :frightened 0.0
             :home-timer delay}))
        ghost-specs))

(defn initial-pac
  []
  (let [[sx sy] (centre-of pac-start)]
    ;; dx/dy is the current heading, ndx/ndy the BUFFERED turn, fx/fy the last
    ;; non-zero heading, which is where the mouth points while stopped.
    {:x sx
     :y sy
     :dx -1
     :dy 0
     :ndx -1
     :ndy 0
     :fx -1
     :fy 0
     :mouth 0.0}))

(defn new-game
  "A fresh world. With `previous`, score and lives carry over and the level
  advances, which is the between-levels reset."
  ([] (new-game nil))
  ([previous]
   {:pac (initial-pac)
    :ghosts (initial-ghosts)
    :dots (initial-dots)
    :score (if previous (:score previous) 0)
    :lives (if previous (:lives previous) 3)
    :level (if previous (inc (:level previous)) 1)
    :mode-timer 7.0
    :chase? false
    :combo 0
    :message nil
    :message-timer 0.0
    :cleared? false
    :over? false
    :clock 0.0}))

;; --- movement ----------------------------------------------------------------

(defn tile-of ^long [v] (long (Math/floor v)))

(defn step-entity
  "Advance an entity by speed*dt, deciding its direction only at tile centres.

  `entity` is {:x :y :dx :dy}; `decide` is (fn [tx ty dx dy] -> [dx dy]) called
  when the step reaches the centre of the current tile, and a blocked or zero
  choice stops there; `walls?` is the entity's own blocking predicate. Returns
  {:x :y :dx :dy}."
  [{:keys [x y dx dy]} {:keys [speed dt decide walls?]}]
  (let [dist (* speed dt)
        cx (+ (Math/floor x) 0.5)
        cy (+ (Math/floor y) 0.5)
        to-centre (+ (* dx (- cx x)) (* dy (- cy y)))]
    (if (and (>= to-centre -1.0e-9) (<= to-centre dist))
      (let [tx (tile-of cx)
            ty (tile-of cy)
            [ndx ndy] (decide tx ty dx dy)
            leftover (- dist to-centre)]
        (if (or (and (zero? ndx) (zero? ndy))
                (walls? (+ tx ndx) (+ ty ndy)))
          {:x cx :y cy :dx ndx :dy ndy}
          {:x (mod (+ cx (* ndx leftover)) MW)
           :y (+ cy (* ndy leftover))
           :dx ndx
           :dy ndy}))
      {:x (mod (+ x (* dx dist)) MW)
       :y (+ y (* dy dist))
       :dx dx
       :dy dy})))

(def ^:const PAC-SPEED 5.6)

(defn move-pac
  [pac dt]
  (let [{:keys [ndx ndy]} pac
        decide (fn [tx ty dx dy]
                 (cond
                   ;; The buffered turn wins whenever it is possible here.
                   (and (or (not= ndx dx) (not= ndy dy))
                        (not (wall? (+ tx ndx) (+ ty ndy))))
                   [ndx ndy]
                   (not (wall? (+ tx dx) (+ ty dy))) [dx dy]
                   :else [0 0]))
        moved (step-entity pac {:speed PAC-SPEED
                                :dt dt
                                :decide decide
                                :walls? wall?})
        moving? (not (and (zero? (:dx moved)) (zero? (:dy moved))))]
    (merge pac moved
           {:fx (if moving? (:dx moved) (:fx pac))
            :fy (if moving? (:dy moved) (:fy pac))
            :mouth (+ (:mouth pac) (* dt (if moving? 9.0 0.0)))})))

(defn ghost-target
  "The classic personalities, in tile coordinates."
  [g pac ghosts]
  (let [{:keys [x y fx fy]} pac
        ptx (tile-of x)
        pty (tile-of y)]
    (case (:name g)
      :blinky [ptx pty]
      :pinky [(+ ptx (* 4 fx)) (+ pty (* 4 fy))]
      :inky (let [b (first (filter #(= :blinky (:name %)) ghosts))
                  ax (+ ptx (* 2 fx))
                  ay (+ pty (* 2 fy))]
              [(- (* 2 ax) (tile-of (:x b)))
               (- (* 2 ay) (tile-of (:y b)))])
      :clyde (let [d (+ (Math/abs (- (:x g) x)) (Math/abs (- (:y g) y)))]
               (if (> d 8) [ptx pty] (:scatter g))))))

(defn ghost-choose-dir
  "At a tile centre, the direction that gets closest to the target without
  reversing. Reversing is forbidden in the original too, which is what makes a
  ghost commit to a route. A frightened ghost picks at random instead."
  [[tx ty] [dx dy] {:keys [target frightened?]}]
  (let [opts (vec (for [[ndx ndy] [[0 -1] [-1 0] [0 1] [1 0]]
                        :when (and (not (and (= ndx (- dx)) (= ndy (- dy))))
                                   (not (ghost-wall? (+ tx ndx) (+ ty ndy))))]
                    [ndx ndy]))
        ;; A dead end leaves nothing but the reversal.
        opts (if (seq opts) opts [[(- dx) (- dy)]])]
    (if frightened?
      (rand-nth opts)
      (let [[gx gy] target]
        (apply min-key
               (fn [[ndx ndy]]
                 (let [ax (+ tx ndx)
                       ay (+ ty ndy)]
                   (+ (* (- ax gx) (- ax gx)) (* (- ay gy) (- ay gy)))))
               opts)))))

(def ^:const GHOST-SPEED 4.6)
(def ^:const GHOST-SPEED-SCARED 3.1)

(defn move-ghost
  [g {:keys [pac ghosts chase? dt]}]
  (if (pos? (:home-timer g))
    ;; Waiting inside the house until released.
    (update g :home-timer - dt)
    (let [g (update g :frightened #(max 0.0 (- % dt)))
          frightened? (pos? (:frightened g))
          target (if chase? (ghost-target g pac ghosts) (:scatter g))
          decide (fn [tx ty dx dy]
                   ;; Still inside the house: head for the door first.
                   (ghost-choose-dir [tx ty] [dx dy]
                                     {:target (if (= \G (tile-at tx ty))
                                                door-exit
                                                target)
                                      :frightened? frightened?}))]
      (merge g (step-entity g {:speed (if frightened?
                                        GHOST-SPEED-SCARED
                                        GHOST-SPEED)
                               :dt dt
                               :decide decide
                               :walls? ghost-wall?})))))

;; --- game rules --------------------------------------------------------------

(defn eat
  [s]
  (let [pac (:pac s)
        tx (tile-of (:x pac))
        ty (tile-of (:y pac))]
    (if-not (contains? (:dots s) [tx ty])
      s
      (let [pellet? (= \o (tile-at tx ty))]
        (cond-> (-> s
                    (update :dots disj [tx ty])
                    (update :score + (if pellet? 50 10)))
          ;; A pellet frightens every ghost and restarts the eat-combo, which is
          ;; what makes 200/400/800/1600 possible.
          pellet? (-> (assoc :combo 0)
                      (update :ghosts
                              (fn [gs] (mapv #(assoc % :frightened 7.0) gs)))))))))

(defn caught-by-ghost
  [s]
  (let [lives (dec (:lives s))]
    (if (pos? lives)
      (assoc s
             :lives lives
             :message "CAUGHT!"
             :message-timer 1.2
             :pac (initial-pac)
             :ghosts (initial-ghosts))
      (assoc s :lives 0 :over? true :message "GAME OVER"))))

(defn eat-ghost
  [s i g]
  (let [combo (inc (:combo s))
        [hx hy] (centre-of (nth house-slots 1))]
    (-> s
        ;; 200, 400, 800, 1600 within one pellet.
        (update :score + (* 200 (bit-shift-left 1 (dec combo))))
        (assoc :combo combo)
        (assoc-in [:ghosts i] (merge g {:x hx
                                        :y hy
                                        :dx 0
                                        :dy -1
                                        :frightened 0.0
                                        :home-timer 1.5})))))

(defn collide
  [s]
  (let [pac (:pac s)]
    (reduce (fn [s i]
              (let [g (get-in s [:ghosts i])
                    d (+ (Math/abs (- (:x g) (:x pac)))
                         (Math/abs (- (:y g) (:y pac))))]
                (cond
                  (> d 0.75) s
                  (pos? (:frightened g)) (eat-ghost s i g)
                  :else (caught-by-ghost s))))
            s
            (range (count (:ghosts s))))))

(def ^:const SCATTER-SECONDS 20.0)
(def ^:const CHASE-SECONDS 7.0)

(defn advance-mode
  "Scatter and chase alternate on a timer, as in the original."
  [s dt]
  (let [t (- (:mode-timer s) dt)]
    (if (pos? t)
      (assoc s :mode-timer t)
      (assoc s
             :chase? (not (:chase? s))
             :mode-timer (if (:chase? s) CHASE-SECONDS SCATTER-SECONDS)))))

(def KEY-LEFT (:left enums/keyboard-key))
(def KEY-RIGHT (:right enums/keyboard-key))
(def KEY-UP (:up enums/keyboard-key))
(def KEY-DOWN (:down enums/keyboard-key))
(def KEY-A (:a enums/keyboard-key))
(def KEY-D (:d enums/keyboard-key))
(def KEY-W (:w enums/keyboard-key))
(def KEY-S (:s enums/keyboard-key))
(def KEY-ENTER (:enter enums/keyboard-key))

(def key->dir
  {KEY-LEFT [-1 0] KEY-A [-1 0]
   KEY-RIGHT [1 0] KEY-D [1 0]
   KEY-UP [0 -1] KEY-W [0 -1]
   KEY-DOWN [0 1] KEY-S [0 1]})

(defn drain-key-queue
  "Every key-down raylib saw since the last frame, drained from its key QUEUE.

  IsKeyDown and IsKeyPressed both read POLLED state. PollInputEvents copies
  current to previous and then lets GLFW's callback update current, so a press
  that goes down AND up inside one poll leaves no trace in either one. The
  queue is different: the callback appends to it on every key-down and only the
  app drains it, so a tap shorter than a frame still shows up here.

  That is the difference between a key a person holds and a synthetic one, and
  it is why a recorded demo can steer this game at all. Drain it every frame
  whether or not anything wants the result, or it backs up."
  []
  (loop [acc []]
    (let [k (rck/get-key-pressed)]
      (if (zero? k) acc (recur (conj acc k))))))

(defn steer?
  "True while `k` is held, and on the single frame it is first pressed.

  A tap counts as well as a hold. IsKeyDown is only true on the frames the key
  is physically down, so a press shorter than one frame is lost; IsKeyPressed
  latches for exactly one frame, which is what catches it. Reading both means a
  quick tap buffers a turn for a human, and it is also the difference between a
  synthetic keystroke steering this game and doing nothing at all, since a
  posted CGEvent key press has no measurable duration."
  [k]
  (or (rck/is-key-down? k) (rck/is-key-pressed? k)))

(defn read-input
  "Buffer a steering direction; `step-entity` applies it at the next legal tile
  centre. ENTER restarts once the game is over."
  [s]
  (let [queued (drain-key-queue)
        held (cond
               (or (steer? KEY-LEFT) (steer? KEY-A)) [-1 0]
               (or (steer? KEY-RIGHT) (steer? KEY-D)) [1 0]
               (or (steer? KEY-UP) (steer? KEY-W)) [0 -1]
               (or (steer? KEY-DOWN) (steer? KEY-S)) [0 1])
        ;; The most recent tap wins over whatever is being held.
        tapped (some key->dir (reverse queued))
        dir (or tapped held)]
    (cond
      (and (:over? s) (or (rck/is-key-pressed? KEY-ENTER)
                          (boolean (some #{KEY-ENTER} queued)))) (new-game)
      dir (update s :pac assoc :ndx (first dir) :ndy (second dir))
      :else s)))

(defn step
  "One frame: read input, advance the world, and answer the next state. dt is
  clamped so a stalled frame cannot step an entity through a wall."
  [s]
  (let [s (read-input s)
        dt (min 0.05 (double (rct/get-frame-time)))
        s (update s :clock + dt)]
    (cond
      (:over? s) s
      ;; The LEVEL CLEARED message shows for its two seconds, then the reset.
      (and (:cleared? s) (zero? (:message-timer s))) (new-game s)
      :else
      (let [s (update s :message-timer #(max 0.0 (- % dt)))
            s (if (zero? (:message-timer s)) (assoc s :message nil) s)
            s (advance-mode s dt)
            s (update s :pac move-pac dt)
            s (eat s)
            s (update s :ghosts
                      (fn [gs]
                        (mapv #(move-ghost % {:pac (:pac s)
                                              :ghosts gs
                                              :chase? (:chase? s)
                                              :dt dt})
                              gs)))
            s (collide s)]
        (if (empty? (:dots s))
          (assoc s :message "LEVEL CLEARED" :message-timer 2.0 :cleared? true)
          s)))))

;; --- drawing -----------------------------------------------------------------

(defn draw-maze!
  [dots blink?]
  (dotimes [y MH]
    (dotimes [x MW]
      (let [c (tile-at x y)
            sx (px x)
            sy (py y)]
        (cond
          (= \# c)
          (do (rsb/draw-rectangle! sx sy CELL CELL WALL-EDGE)
              (rsb/draw-rectangle! (+ sx 3) (+ sy 3) (- CELL 6) (- CELL 6) WALL))
          (= \- c)
          (rsb/draw-rectangle! sx (+ sy (quot CELL 2) -2) CELL 4 DOOR)))))
  (doseq [[x y] dots]
    ;; Power pellets are bigger and blink; plain dots do neither.
    (let [pellet? (= \o (tile-at x y))]
      (when (or (not pellet?) blink?)
        (rsb/draw-circle! (+ (px x) (quot CELL 2))
                          (+ (py y) (quot CELL 2))
                          (if pellet? (float 7.0) (float 3.0))
                          PELLET)))))

(defn heading-deg
  "The heading (fx, fy) as a DrawCircleSector angle.

  raylib measures a sector from the positive x axis and y grows downward, so 0
  points right and the angle increases clockwise on screen. That makes the
  conversion just atan2 in degrees, with no quarter-turn to correct for."
  [fx fy]
  (Math/toDegrees (Math/atan2 (double fy) (double fx))))

(defn draw-pac!
  [{:keys [x y fx fy mouth]}]
  ;; The mouth is the wedge the sector does NOT cover: sweep from its far lip
  ;; clockwise all the way round to its near one. It opens and closes as :mouth
  ;; advances, which only happens while Pac-Man is moving.
  (let [open (Math/toDegrees (* 0.42 (+ 1.0 (Math/sin mouth))))
        from (+ (heading-deg fx fy) open)]
    (rsb/draw-circle-sector! {:x (float (px x)) :y (float (py y))}
                             (float (* 0.46 CELL))
                             (float from)
                             (float (+ from (- 360.0 (* 2.0 open))))
                             28
                             PELLET)))

(defn draw-ghost!
  [g blink?]
  (let [cx (long (px (:x g)))
        cy (long (py (:y g)))
        r (long (* 0.44 CELL))
        color (cond
                (and (pos? (:frightened g)) blink?) LABEL
                (pos? (:frightened g)) SCARED
                :else (:color g))]
    ;; A dome over a body, with three feet along the bottom.
    (rsb/draw-circle! cx (- cy 2) (float r) color)
    (rsb/draw-rectangle! (- cx r) (- cy 2) (* 2 r) (+ r 2) color)
    (dotimes [i 3]
      (rsb/draw-circle! (+ (- cx r) (* i r) (quot r 2))
                        (+ cy r)
                        (float (/ (double r) 2.6))
                        color))
    ;; The eyes look along the direction of travel; a frightened ghost has none.
    (let [ex (long (* 4 (:dx g)))
          ey (long (* 4 (:dy g)))]
      (rsb/draw-circle! (- cx 5) (- cy 5) (float 5.0) LABEL)
      (rsb/draw-circle! (+ cx 5) (- cy 5) (float 5.0) LABEL)
      (when-not (pos? (:frightened g))
        (rsb/draw-circle! (+ (- cx 5) ex) (+ (- cy 5) ey) (float 2.5) PUPIL)
        (rsb/draw-circle! (+ cx 5 ex) (+ (- cy 5) ey) (float 2.5) PUPIL)))))

(defn draw-hud!
  [s]
  (rtd/draw-text! (str "SCORE " (:score s)) 20 20 28 LABEL)
  (rtd/draw-text! (str "LEVEL " (:level s)) (- W 340) 20 28 LABEL)
  (dotimes [i (:lives s)]
    (rsb/draw-circle! (+ (- W 150) (* i 34)) 33 (float 11.0) PELLET))
  (when-let [m (:message s)]
    ;; MeasureText is how a scalar API centres text: ask, then place.
    (let [size 46]
      (rtd/draw-text! m
                      (quot (- W (rtd/measure-text m size)) 2)
                      (- (quot H 2) 24)
                      size
                      (if (:over? s) OVER PELLET)))))

(defn draw-state!
  [s]
  (rcd/clear-background! BACKGROUND)
  (draw-maze! (:dots s) (< (mod (:clock s) 0.4) 0.25))
  (draw-pac! (:pac s))
  (doseq [g (:ghosts s)]
    (draw-ghost! g (< (mod (:clock s) 0.25) 0.12)))
  (draw-hud! s))

;; --- the loop ----------------------------------------------------------------

(defn maybe-screenshot!
  "Dump one PNG on frame `at`, which is visual proof a frame rendered with
  nobody at the keyboard. raylib defers batched geometry until EndDrawing, so
  the batch is flushed first or the capture misses everything drawn this frame.
  raylib resolves the path against the working directory fixed at InitWindow,
  so pass a basename."
  [shot-path frame at]
  (when (and shot-path (= frame at))
    (draw-render-batch-active!)
    (take-screenshot! shot-path)
    (binding [*out* *err*] (println "[raylib-pacman] SHOT" shot-path))))

(defn -main
  "With no argument the window stays open until you close it. `seconds` quits on
  a deadline and `shot` writes one PNG at frame `shot-frame` (default 60), which is what makes the
  example runnable unattended.

    clojure -M:pacman             play it
    clojure -M:pacman 10          quit after ten seconds
    clojure -M:pacman 3 out.png 75   quit after three, capture frame 75

  `seconds` counts GAME time, summed from the clamped per-frame dt, rather than
  wall time. A warm-up frame on a JIT'd runtime can cost seconds on its own, and
  a wall clock charges that against the deadline; the game clock does not,
  because dt is clamped to 0.05 before it is added."
  [& args]
  (let [[seconds shot-path shot-frame] args
        long-arg (fn [v] (try (some-> v Long/parseLong)
                              (catch Exception _ nil)))
        secs (long-arg seconds)
        at (or (long-arg shot-frame) 60)]
    (rcw/init-window! W H "raylib-clj - pac-man")
    (rct/set-target-fps! 60)
    (let [final (loop [frame 0
                       s (new-game)]
                  (if (rcw/window-should-close?)
                    s
                    (let [s' (step s)]
                      (rcd/begin-drawing!)
                      (draw-state! s')
                      (maybe-screenshot! shot-path frame at)
                      (rcd/end-drawing!)
                      (if (and secs (pos? secs) (> (:clock s') secs))
                        s'
                        (recur (inc frame) s')))))]
      (rcw/close-window!)
      (println "final score:" (:score final) "level:" (:level final)))))
