(ns rumext.examples.refs
  (:require [rumext.core :as rmx]))

(rmx/defcs ta
  {:init
   (fn [state]
     (assoc state :ta (rmx/create-ref)))
   :after-render
   (fn [state]
     (let [ta (rmx/ref-node (:ta state))]
       (set! (.-height (.-style ta)) "0")
       (set! (.-height (.-style ta)) (str (+ 2 (.-scrollHeight ta)) "px")))
     state) }
  [{:keys [::rmx/react-component ta] :as state}]
  [:textarea
   {:ref ta
    :style { :width   "100%"
            :padding "10px"
            :font    "inherit"
            :outline "none"
            :resize  "none"}
    :default-value "Auto-resizing\ntextarea"
    :placeholder "Auto-resizing textarea"
    :on-change (fn [_] (rmx/request-render react-component)) }])

(rmx/defc refs
  []
  [:div
   (ta)])

(defn mount! [el]
  (rmx/mount (refs) el))
