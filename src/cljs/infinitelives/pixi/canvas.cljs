(ns
    ^{:doc "Functions for building and manipulating the canvas DOM element"}
  infinitelives.pixi.canvas
  (:require [cljs.core.async :refer [<!]]
            [infinitelives.utils.events :as events]
            [infinitelives.utils.dom :as dom]
            [infinitelives.utils.console :refer [log]]
            [PIXI])

  (:require-macros [cljs.core.async.macros :refer [go]])
)


(defn make
  "make a new pixi canvas, or initialise pixi with an existing canvas.

  Pass in...

  :expand        if true makes the canvas take the entire window
  :engine        can be :webgl :canvas or :auto (default :auto)

  and either:

  :canvas        a DOM element to use as the canvas

  or:

  :x             x position for the new canvas
  :y             y position for the new canvas
  :width         width of new canvas
  :height        height of new canvas"
  [{:keys [expand x y width height canvas engine]
      :or {expand false
           x 0
           y 0
           width 800
           height 600
           engine :auto}}]
  (let [fswidth (.-innerWidth js/window)
        fsheight (.-innerHeight js/window)

        ;; arguments for renderer
        wid (if expand fswidth width)
        hig (if expand fsheight height)
        opts #js {"view" canvas
                  "transparent" false
                  "antialias" false
                  "preserveDrawingBuffer" false
                  "resolution" 1
                  "clearBeforeRender" true
                  "autoResize" false
                  "imageSmoothingEnabled" false
                  }

        ;; make the renderer
        rend (case engine
               :webgl (PIXI/WebGLRenderer. wid hig opts)
               :canvas (PIXI/CanvasRenderer. wid hig opts)
               (PIXI/autoDetectRenderer. wid hig opts))

        ;; details of the generated renderer
        actual-canvas (.-view rend)
        canvas-width (.-width actual-canvas)
        canvas-height (.-height actual-canvas)]

    (when-not canvas
      ;; custom canvas was generated. we should position it
      ;; and add it to the DOM
      (do
        (dom/set-style! actual-canvas
                          :left (if expand 0 x)
                          :top (if expand 0 y)
                          :position "absolute")
        (dom/append! (.-body js/document) actual-canvas)))

    (let [wind-width (if expand fswidth canvas-width)
          wind-height (if expand fsheight canvas-height)
          middle-x (Math/round (/ wind-width 2))
          middle-y (Math/round (/ wind-height 2))]

      (.resize rend wind-width wind-height))

    ;; return canvas and pixi renderer
    {
     :renderer rend
     :canvas (or canvas actual-canvas)
     }))

(defn make-stage
  "Layout the stage structure"
  [{:keys [background layers]
      :or {background 0x000000
           layers [:backdrop :below :world :above :ui :effect]}}]

  ;(.log js/console (str layers))
  (let [stage (PIXI/Stage. background)
        containers (map #(PIXI/DisplayObjectContainer.) layers)
        ]
    {
     :stage stage
     :layer
     (into {}
           (for [[k v] (partition 2 (interleave layers containers))]
             [k v]))}))


(defn- center-container! [canvas layer]
  (let [canvas-width (.-width canvas)
        canvas-height (.-height canvas)
        middle-x (Math/round (/ canvas-width 2))
        middle-y (Math/round (/ canvas-height 2))]

    (log (str "w:" canvas-width " h:" canvas-height))

    ;; start with world centered
    (set! (.-position.x layer) middle-x)
    (set! (.-position.y layer) middle-y)))


(defn init
  "Initialise the canvas element. Pass in optional keys

  :background    background colour (default 0x000000)
  :expand        if true makes the canvas take the entire window
  :engine        can be :webgl :canvas or :auto (default :auto)
  :layers        A list of keywords to refer to layers, from bottom to top

  and either:

  :canvas        a DOM element to use as the canvas

  or:

  :x             x position for the new canvas
  :y             y position for the new canvas
  :width         width of new canvas
  :height        height of new canvas
  "
  [opts]
  (let [{:keys [stage renderer canvas layer] :as world}
        (into (make opts)
              (make-stage opts))]
    ;; add the stages to the canvas
    (doall
     (map
      (fn [[name layer-obj]]
        (log "adding to:" (str stage) " layer:" (str layer-obj))
        (.addChild stage layer-obj)
        (center-container! canvas layer-obj)
        )
      layer))

    ;; do the first render
    (.render renderer stage)

    (let [
          render-fn (fn [] (.render renderer stage))
          resize-fn (fn [width height]
                      (.resize renderer width height)
                      (doall (map (partial center-container! canvas)
                                  (map second layer))))
          expand-fn (fn [] (resize-fn (.-innerWidth js/window)
                                      (.-innerHeight js/window)))

          resizer-loop
          (when (:expand opts) (let [c (events/new-resize-chan)]
                           (go (while true
                                 (let [[width height] (<! c)]
                                   (resize-fn width height)
                                   (render-fn))))))]

      (into
       world
       {
        :render-fn render-fn
        :resize-fn resize-fn
        :expand-fn expand-fn}))))
