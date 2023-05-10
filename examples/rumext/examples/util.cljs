(ns rumext.examples.util
  (:require
   [rumext.v2 :as mf]
   [goog.dom :as dom]
   [okulary.core :as l]))

(defonce *clock (l/atom (.getTime (js/Date.))))
(defonce *color (l/atom "#FA8D97"))
(defonce *speed (l/atom 160))

;; Start clock ticking
(defn tick []
  (reset! *clock (.getTime (js/Date.))))

(defonce sem (js/setInterval tick @*speed))

(defn format-time [ts]
  (-> ts (js/Date.) (.toISOString) (subs 11 23)))

(defn el [id]
  (dom/getElement id))

(mf/defc watches-count
  [{:keys [iref] :as props}]
  (let [state (mf/use-state 0)]
    (mf/use-effect
     (mf/deps iref)
     (fn []
       (let [sem (js/setInterval #(swap! state inc) 1000)]
         #(do
            (js/clearInterval sem)))))

    [:span (.-size (.-watches ^js iref))]))

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

;; (mf/def board-stats
;;   :mixins [mf/reactive]
;;   :render
;;   (fn [own [*board *renders]]
;;     [:div.stats
;;      "Renders: "       (mf/react *renders)
;;      [:br]
;;      "Board watches: " (watches-count *board)
;;      [:br]
;;      "Color watches: " (watches-count *color) ]))
