(ns rumext.examples.util
  (:require [rumext.core :as rmx :refer-macros [defc defcs]]))

(def *clock (atom (.getTime (js/Date.))))
(def *color (atom "#FA8D97"))
(def *speed (atom 150))

;; Start clock ticking
(defn tick []
  (reset! *clock (.getTime (js/Date.))))

(defonce sem (js/setInterval tick @*speed))


(defn format-time [ts]
  (-> ts (js/Date.) (.toISOString) (subs 11 23)))

(defn el [id]
  (js/document.getElementById id))

(defn periodic-refresh
  [period]
  {:did-mount
   (fn [state]
     (let [rcomp (::rmx/react-component state)
           sem (js/setInterval #(rmx/request-render rcomp) period)]
       (assoc state ::interval sem)))
   :will-unmount
   (fn [state]
     (js/clearInterval (::interval state)))})

;; Using custom mixin

(defc watches-count
  {:mixins [(periodic-refresh 1000)]}
  [ref]
  [:span (count (.-watches ref))])

;; Generic board utils

(def ^:const board-width 19)
(def ^:const board-height 10)

(defn prime?
  [i]
  (and (>= i 2)
       (empty? (filter #(= 0 (mod i %)) (range 2 i)))))

(defn initial-board
  []
  (->> (map prime? (range 0 (* board-width board-height)))
       (partition board-width)
       (mapv vec)))

(defc board-stats
  {:mixins [rmx/reactive]}
  [[*board *renders]]
  [:div.stats
   "Renders: "       (rmx/react *renders)
   [:br]
   "Board watches: " (watches-count *board)
   [:br]
   "Color watches: " (watches-count *color) ])
