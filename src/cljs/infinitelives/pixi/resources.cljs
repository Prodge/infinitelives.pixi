(ns infinitielives.pixi.resources)

;; where we store all the loaded textures keyed by name
(defonce !textures (atom {}))

(defn progress-texture
  "Draws an empty box that can serve as a default progress bar for preloading images"
  [fraction {:keys [empty-colour full-colour
                    border-colour border-width draw-border
                    width height
                    highlight highlight-offset highlight-width
                    lowlight lowlight-offset lowlight-width]
             :or {empty-colour 0x000000
                  full-colour 0x808080
                  border-colour 0xffffff
                  border-width 2
                  draw-border false
                  width 600
                  height 40
                  highlight-offset 0
                  highlight-width 1
                  lowlight-offset 0
                  lowlight-width 1
                  }
             :as options}]
  (let [box (PIXI/Graphics.)]
    (doto box
      (.beginFill empty-colour)
      (.lineStyle 0 border-colour)
      (.drawRect 0 0 width height)
      (.lineStyle 0 border-colour)
      (.beginFill full-colour)
      (.drawRect border-width border-width (* (if (< fraction 1) fraction 1) (- width border-width border-width)) (- height border-width border-width ))
      .endFill)

    (let [bw (* (if (< fraction 1) fraction 1) width)
          x1 (+ border-width highlight-offset)
          x2 (- bw highlight-offset)
          y1 (+ border-width lowlight-offset)
          y2 (- height border-width lowlight-offset)]
      (when (> bw 0)
        (when highlight
          (doto box
              (.lineStyle highlight-width highlight)
              (.moveTo x1 y2)
              (.lineTo x1 y1)
              (.lineTo x2 y1)))
        (when lowlight
          (doto box
            (.lineStyle lowlight-width lowlight)
            (.moveTo x1 y2)
            (.lineTo x2 y2)
            (.lineTo x2 y1)))))

    (when draw-border
      (doto box
        (.lineStyle border-width border-colour)
        (.drawRect 0 0 width height)))

    (.generateTexture box false)))


(defn add-prog-bar [stage options]
  (let [s (sprite/make-sprite (progress-texture 0 options))]
    (set! (.-alpha s) 0)
    (.addChild stage s)
    s))


(defn get-texture [key scale]
  (scale (key @!textures)))


;; setup a pixi texture keyed by the tail of its filename
(defn- register-texture
  [url img]
  (when (ends-with? url ".png")
    (swap! =textures=
           assoc (url-keyword url)
           {
            :linear
            (PIXI/Texture.fromImage
             url true (aget js/PIXI.scaleModes "LINEAR"))

            ;; this is a hack adding # to the tail of a url so pixi doesnt use the other
            ;; linear version
            ;; SEE: https://github.com/GoodBoyDigital/pixi.js/issues/1724
            :nearest
            (PIXI/Texture.fromImage
             (str url "#") true (aget js/PIXI.scaleModes "NEAREST"))})))

(defn load-urls
  "loads each url in the passed in list as an image. Updates the progress
as it goes with
  a percentage and a thumbnail. Once complete, displays all the images
fullsize."
  [urls progress-bar & options]
  (let [options (into {} options)]
    (let [finished (chan)                         ;; make our channel to
          num-urls (count urls)                   ;; how many urls
          images (doall                           ;; start loading all the urls
                  (map (fn [src]
                         (let [i (js/Image.)]
                           (set! (.-onload i) #(put! finished [src i]))
                           (set! (.-onerror i) #(put! finished [src i]))
                           (set! (.-onabort i) #(js/alert "abort"))
                           (set! (.-src i) src)
                           i))
                       urls))]
      (go
        (loop [i 1]
          (let [[url img] (<! finished)] ;; a new image has finished loading
            (when (:debug-delay options)
              (<! (timeout (* (:debug-delay options) 1000)))) ;; artificial random delay (up to 1 ec)

            ;; setup a pixi texture keyed by the tail of its filename
            (register-texture url img)

            ;; update progress bar and add image
            (.setTexture progress-bar
                         (progress-texture (/ i num-urls) options))

            ;; more images?
            (when (< i num-urls)
              (recur (inc i)))))))))

(defn load-resources [s urls & {:keys [fade-in fade-out]
                      :or {fade-in 0.5 fade-out 0.5}
                      :as options}]
  (log "!")
  (let [c (chan)
        b (add-prog-bar s options)]
    (go
      ;; fade in
      (log "fade in")
      (<! (sprite/fadein b :duration fade-in))
      (log "done")

      ;; show load progress
      (<! (apply (partial load-urls urls b) options))

      ;; load is done. return message
      (>! c true)

      ;; delay a tiny bit
      (<! (timeout 300))

      ;; fadeout
      (<! (sprite/fadeout b :duration fade-out))

      ;; remove progress bar sprite
      (.removeChild s b)

      (close! c))
    c))