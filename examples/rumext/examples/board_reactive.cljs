(ns rumext.examples.board-reactive
  (:require [rumext.core :as rmx :refer-macros [defc]]
            [rumext.examples.util :as util]
            [lentes.core :as l]))

;; Reactive drawing board

(def *board (atom (util/initial-board)))
(def *board-renders (atom 0))

(defc cell
  {:mixins [rmx/reactive]
   :did-update (fn [state]
                 (swap! *board-renders inc)
                 state)}
  [x y]
  (let [celref (l/derive (l/in [y x]) *board)]
    ;; each cell subscribes to its own cursor inside a board
    ;; note that subscription to color is conditional:
    ;; only if cell is on (@cursor == true),
    ;; this component will be notified on color changes
    [:div.art-cell
     {:style {:background-color (when (rmx/react celref)
                                  (rmx/react util/*color))}
      :on-mouse-over (fn [_] (swap! celref not) nil)}]))


(defc board-reactive []
  [:div.artboard
   (for [y (range 0 util/board-height)]
     [:div.art-row {:key y}
      (for [x (range 0 util/board-width)]
        ;; this is how one can specify React key for component
        (-> (cell x y)
            (rmx/with-key [x y])))])
   (util/board-stats *board *board-renders)])


(defn mount! [el]
  (rmx/mount (board-reactive) el))
