(ns rumext.examples.timer-static
  (:require [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

(mf/defc timer-static
  [{:keys [label ts] :as props}]
  [:div label ": "
   [:span {:style {:color @util/*color}} (util/format-time ts)]])

(defn mount! [el]
  (letfn [(ctor [ts]
            (mf/html [:& timer-static {:label "Static" :ts ts :foo [1 2 3]}]))]
    (mf/mount (ctor @util/*clock) el)
    (add-watch util/*clock :timer-static
               (fn [_ _ _ ts]
                 (mf/mount (ctor ts) el)))))
