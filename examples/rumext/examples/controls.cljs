(ns rumext.examples.controls
  (:require [rumext.alpha :as mf]
            [rumext.examples.util :as util]))


;; generic “atom editor” component
(mf/defc input
  {:wrap [mf/reactive]}
  [{:keys [color] :as props}]
  [:input {:type "text"
           :value (mf/react color)
           :style {:width 100}
           :on-change #(reset! color (.. % -target -value))}])

;; Raw top-level component, everything interesting is happening inside
(mf/defc controls
  []
  [:dl
   [:dt "Color: "]
   [:dd [:& input {:color util/*color}]]
   ;; Binding another component to the same atom will keep 2 input boxes in sync
   [:dt "Clone: "]
   [:dd [:& input {:color util/*color}]]
   [:dt "Color: "]
   [:dd (util/watches-count util/*color) " watches"]

   [:dt "Tick: "]
   [:dd [:& input {:color util/*speed}] " ms"]
   [:dt "Time:"]
   [:dd (util/watches-count util/*clock) " watches"]
   ])


(defn mount! [el]
  (mf/mount (mf/element controls) el))

