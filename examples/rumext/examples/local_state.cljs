(ns rumext.examples.local-state
  (:require [rumext.core :as rmx]
            [rumext.examples.util :as util]))

;; Local component state

(rmx/defcs local-state
  {:mixins [(rmx/local 0)]}
  [state title]
  (let [*count (::rmx/local state)]
    [:div
     {:style {"-webkit-user-select" "none"
              "cursor" "pointer"}
      :on-click (fn [_] (swap! *count inc)) }
     title ": " @*count]))

(defn mount! [el]
  (rmx/mount (local-state "Clicks count") el))
