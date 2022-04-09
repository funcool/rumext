(ns rumext.examples.controls
  (:require [goog.dom :as dom]
            [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

;; generic “atom editor” component
(mf/defc input
  [{:keys [color] :as props}]
  (let [value (mf/deref color)]
    [:input {:type "text"
             :value value
             :style {:width 100}
             :on-change #(reset! color (.. % -target -value))}]))

;; Raw top-level component, everything interesting is happening inside
(mf/defc controls
  [props]
  [:dl
   [:dt "Color: "]
   [:dd
    [:& input {:color util/*color}]]
   ;; Binding another component to the same atom will keep 2 input boxes in sync
   [:dt "Clone: "]
   [:dd
    (mf/element input {:color util/*color})]
   [:dt "Color: "]
   [:dd (util/watches-count {:iref util/*color}) " watches"]

   [:dt "Tick: "]
   [:dd [:& input {:color util/*speed}] " ms"]
   [:dt "Time:"]
   [:dd (util/watches-count {:iref util/*clock}) " watches"]
   ])

(defonce root (mf/create-root (dom/getElement "controls")))

(defn mount! []
  (mf/mount root (mf/element controls)))

