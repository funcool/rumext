(ns rumext.examples.refs
  (:require [rumext.core :as mx]))

(mx/defcs ta
  {:init
   (fn [own]
     (assoc own ::ta (mx/create-ref)))

   :after-render
   (fn [own]
     (let [ta (mx/ref-node (::ta own))]
       (set! (.-height (.-style ta)) "0")
       (set! (.-height (.-style ta)) (str (+ 2 (.-scrollHeight ta)) "px")))
     own)}

  [own]
  [:textarea
   {:ref (::ta own)
    :style { :width   "100%"
            :padding "10px"
            :font    "inherit"
            :outline "none"
            :resize  "none"}
    :default-value "Auto-resizing\ntextarea"
    :placeholder "Auto-resizing textarea"
    :on-change (fn [_] (-> (mx/react-component own)
                           (mx/request-render)))}])

(mx/defc refs
  []
  [:div (ta)])

(defn mount! [el]
  (mx/mount (refs) el))
