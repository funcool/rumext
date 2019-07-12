(ns rumext.examples.errors
  (:require [rumext.core :as rmx]))

(rmx/defc faulty-render
  [msg]
  (throw (ex-info msg {})))


(rmx/defc faulty-mount
  {:did-mount
   (fn [state]
     (let [[msg] (::rmx/args state)]
       (throw (ex-info msg {}))))}
  [msg]
  "Some test you’ll never see")


(rmx/defcs child-error
  {:did-catch
   (fn [state error info]
     (assoc state ::error error))}
  [{error ::error, c ::rmx/react-component} comp msg]
  (if (some? error)
    [:span "CAUGHT: " (str error)]
    [:span "No error: " (comp msg)]))

(rmx/defc errors
  []
  [:span
   (child-error faulty-render "render error")
   #_(child-error faulty-mount "mount error")])

(defn mount! [el]
  (rmx/mount (errors) el))
