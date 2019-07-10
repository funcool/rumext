(ns rumext.examples.timer-static
  (:require [rumext.core :as rum :refer-macros [defc]]
            [rumext.examples.util :as util]))

(defc timer-static
  {:mixins [rum/reactive]}
  [label ts]
  [:div label ": "
   [:span {:style {:color @util/*color}} (util/format-time ts)]])

(defn mount! [el]
  (rum/mount (timer-static "Static" @util/*clock) el)
  (add-watch util/*clock :timer-static
             (fn [_ _ _ new-val]
               (rum/mount (timer-static "Static" new-val) el))))
