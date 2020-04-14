(ns rumext.examples.refs
  (:require [goog.dom :as dom]
            [rumext.alpha :as mf]))

(mf/defc textarea
  [props]
  (let [ref (mf/use-ref)
        state (mf/use-state 0)]
    (mf/use-layout-effect
     nil
     (fn []
       (let [node (mf/ref-val ref)]
         (set! (.-height (.-style node)) "0")
         (set! (.-height (.-style node)) (str (+ 2 (.-scrollHeight node)) "px")))))

    [:textarea
     {:ref ref
      :style {:width   "100%"
              :padding "10px"
              :font    "inherit"
              :outline "none"
              :resize  "none"}
      :default-value "Auto-resizing\ntextarea"
      :placeholder "Auto-resizing textarea"
      :on-change (fn [_] (swap! state inc))}]))

(mf/defc refs
  []
  [:div
   [:& textarea]])

(defn mount! []
  (mf/mount (mf/element refs) (dom/getElement "refs")))
