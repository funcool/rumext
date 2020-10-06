(ns rumext.examples.board
  (:require
   [goog.dom :as dom]
   [rumext.alpha :as mf]
   [rumext.examples.util :as util]
   [okulary.core :as l]))

;; Reactive drawing board

(def board (atom (util/initial-board)))
(def board-renders (atom 0))

(mf/defc cell
  [{:keys [x y] :as props}]
  (let [ref (mf/use-memo
             (mf/deps x y)
             (fn [] (l/derived (l/in [y x]) board)))

        cell (mf/deref ref)
        color @util/*color]
    [:div.art-cell
     {:style {:background-color (when cell color)}
      :on-mouse-over (fn [_] (swap! board update-in [y x] not) nil)}]))

(mf/defc board-reactive
  []
  [:div.artboard
   (for [y (range 0 util/board-height)]
     [:div.art-row {:key y}
      (for [x (range 0 util/board-width)]
        ;; this is how one can specify React key for component
        [:& cell {:key x :x x :y y}])])])

(defn mount! []
  (mf/mount (mf/element board-reactive)
            (dom/getElement "board"))
  (js/setTimeout (fn []
                   (mf/mount (mf/element board-reactive)
                             (dom/getElement "board")))
                 2000))
