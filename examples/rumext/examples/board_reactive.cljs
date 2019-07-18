(ns rumext.examples.board-reactive
  (:require [rumext.core :as mx]
            [rumext.examples.util :as util]
            [lentes.core :as l]))

;; Reactive drawing board

(def *board (atom (util/initial-board)))
(def *board-renders (atom 0))

(mx/defc cell
  {:mixins [mx/reactive]
   :did-update (fn [state]
                 (swap! *board-renders inc)
                 state)}
  [[x y]]
  (prn "cell" x y)
  (let [celref (l/derive (l/in [y x]) *board)]
    ;; each cell subscribes to its own cursor inside a board
    ;; note that subscription to color is conditional:
    ;; only if cell is on (@cursor == true),
    ;; this component will be notified on color changes
    [:div.art-cell
     {:style {:background-color (when (mx/react celref)
                                  (mx/react util/*color))}
      :on-mouse-over (fn [_] (swap! celref not) nil)}]))


(mx/defc board-reactive
  []
  [:div.artboard
   (for [y (range 0 util/board-height)]
     [:div.art-row {:key y}
      (for [x (range 0 util/board-width)]
        ;; this is how one can specify React key for component
        (-> (cell [x y])
            (mx/with-key [x y])))])
   #_(util/board-stats [*board *board-renders])])


(defn mount! [el]
  (mx/mount (board-reactive) el))
