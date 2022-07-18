(ns rumext.examples.board
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]
   [rumext.examples.util :as util]
   [okulary.core :as l]))

;; Reactive drawing board

(def board (atom (util/initial-board)))
(def board-renders (atom 0))

(mf/defc cell
  [{:keys [x y] :as props}]
  (let [ref   (mf/with-memo [x y]
                (l/derived (l/in [y x]) board))
        cell  (mf/deref ref)
        color (mf/deref util/*color)]
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


(def root (mf/create-root (dom/getElement "board")))

(defn mount! []
  (mf/render! root (mf/element board-reactive))
  (js/setTimeout (fn []
                   (mf/render! root (mf/element board-reactive)))
                 2000))
