(ns rumext.examples.util
  (:require [rumext.core :refer-macros [defc defcs]]
            [rum.core :as rum]))

(def *clock (atom 0))
(def *color (atom "#FA8D97"))
(def *speed (atom 150))

(defn format-time [ts]
  (-> ts (js/Date.) (.toISOString) (subs 11 23)))


(defn el [id]
  (js/document.getElementById id))

(defn periodic-refresh [period]
  {:did-mount
   (fn [state]
     (let [react-comp (:rum/react-component state)
           interval   (js/setInterval #(rum/request-render react-comp) period)]
       (assoc state ::interval interval)))
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
  {:mixins [rum/reactive]}
  [*board *renders]
  [:div.stats
   "Renders: "       (rum/react *renders)
   [:br]
   "Board watches: " (watches-count *board)
   [:br]
   "Color watches: " (watches-count *color) ])
