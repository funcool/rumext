(ns rumext.examples.controls
  (:require [rumext.core :as rum]
            [rumext.examples.util :as util]))


;; generic “atom editor” component
(rum/defc input
  {:mixins [rum/reactive]}
  [ref]
  [:input {:type "text"
           :value (rum/react ref)
           :style {:width 100}
           :on-change #(reset! ref (.. % -target -value))}])


;; Raw top-level component, everything interesting is happening inside
(rum/defc controls
  []
  [:dl
   [:dt "Color: "]
   [:dd (input util/*color)]
   ;; Binding another component to the same atom will keep 2 input boxes in sync
   [:dt "Clone: "]
   [:dd (input util/*color)]
   [:dt "Color: "]
   [:dd (util/watches-count util/*color) " watches"]

   [:dt "Tick: "]
   [:dd (input util/*speed) " ms"]
   [:dt "Time:"]
   [:dd (util/watches-count util/*clock) " watches"]
   ])


(defn mount! [el]
  (rum/mount (controls) el))

