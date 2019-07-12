(ns rumext.examples.timer-static
  (:require [rumext.core :as rmx]
            [rumext.examples.util :as util]))

(rmx/defc timer-static
  [label ts]
  [:div label ": "
   [:span {:style {:color @util/*color}} (util/format-time ts)]])

(defn mount! [el]
  (rmx/mount (timer-static "Static" @util/*clock) el)
  (add-watch util/*clock :timer-static
             (fn [_ _ _ new-val]
               (rmx/mount (timer-static "Static" new-val) el))))
