(ns rumext.examples.util
  (:require [rumext.alpha :as mf]
            [goog.dom :as dom]))

(defonce *clock (atom (.getTime (js/Date.))))
(defonce *color (atom "#FA8D97"))
(defonce *speed (atom 160))

;; Start clock ticking
(defn tick []
  (reset! *clock (.getTime (js/Date.))))

(defonce sem (js/setInterval tick @*speed))

(defn format-time [ts]
  (-> ts (js/Date.) (.toISOString) (subs 11 23)))

(defn el [id]
  (dom/getElement id))

(defn periodic-refresh
  [period]
  {:did-mount
   (fn [state]
     (let [rcomp (::mf/react-component state)
           sem (js/setInterval #(mf/request-render rcomp) period)]
       (assoc state ::interval sem)))
   :will-unmount
   (fn [state]
     (js/clearInterval (::interval state)))})

;; Using custom mixin

(mf/def watches-count
  :mixins [(periodic-refresh 1000)]
  :render
  (fn [own {:keys [iref]}]
    [:span (count (.-watches iref))]))

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

(mf/def board-stats
  :mixins [mf/reactive]
  :render
  (fn [own [*board *renders]]
    [:div.stats
     "Renders: "       (mf/react *renders)
     [:br]
     "Board watches: " (watches-count *board)
     [:br]
     "Color watches: " (watches-count *color) ]))
