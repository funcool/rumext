(ns rumext.examples.errors
  (:require [rumext.core :as mx]))

(mx/defc faulty-render
  [msg]
  (throw (ex-info msg {})))


(mx/defc faulty-mount
  {:did-mount
   (fn [state]
     (let [[msg] (::mx/args state)]
       (throw (ex-info msg {}))))}
  [msg]
  "Some test you’ll never see")


(mx/defcs child-error
  {:did-catch
   (fn [state error info]
     (assoc state ::error error))}
  [{error ::error, c ::mx/react-component} comp msg]
  (if (some? error)
    [:span "CAUGHT: " (str error)]
    [:span "No error: " (comp msg)]))

(mx/defc errors
  []
  [:span
   (child-error faulty-render "render error")
   #_(child-error faulty-mount "mount error")])

(defn mount! [el]
  (mx/mount (errors) el))
