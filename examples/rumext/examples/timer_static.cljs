(ns rumext.examples.timer-static
  (:require [rumext.core :as mx]
            [rumext.func :as mxf]
            [rumext.examples.util :as util]))

(def timer-static
  (mxf/fnc timer-static
    [{:keys [label ts] :as props}]
    [:div label ": "
     [:span {:style {:color @util/*color}} (util/format-time ts)]]))

(defn mount! [el]
  (letfn [(ctor [ts]
            (mx/html [:& timer-static {:label "Static" :ts ts :foo [1 2 3]}]))]
    (mx/mount (ctor @util/*clock) el)
    (add-watch util/*clock :timer-static
               (fn [_ _ _ ts]
                 (mx/mount (ctor ts) el)))))
