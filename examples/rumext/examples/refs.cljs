(ns rumext.examples.refs
  (:require [rumext.core :as rum]))

(rum/defcs ta
  {:init
   (fn [state]
     (assoc state :ta (rum/create-ref)))
   :after-render
   (fn [state]
     (let [ta (rum/ref-node (:ta state))]
       (set! (.-height (.-style ta)) "0")
       (set! (.-height (.-style ta)) (str (+ 2 (.-scrollHeight ta)) "px")))
     state) }
  [{:keys [::rum/react-component ta] :as state}]
  [:textarea
   {:ref ta
    :style { :width   "100%"
            :padding "10px"
            :font    "inherit"
            :outline "none"
            :resize  "none"}
    :default-value "Auto-resizing\ntextarea"
    :placeholder "Auto-resizing textarea"
    :on-change (fn [_] (rum/request-render react-component)) }])

(rum/defc refs
  []
  [:div
   (ta)])

(defn mount! [el]
  (rum/mount (refs) el))
