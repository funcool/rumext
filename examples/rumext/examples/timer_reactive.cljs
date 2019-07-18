(ns rumext.examples.timer-reactive
  (:require [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

(mf/defc timer
  {:wrap [mf/reactive]}
  [props]
  (let [ts (mf/react util/*clock)]
    [:div "Reactive" ": "
     [:span {:style {:color @util/*color}}
      (util/format-time ts)]]))

(defn mount! [el]
  (let [comp (mf/element timer)]
    (mf/mount comp el)))

