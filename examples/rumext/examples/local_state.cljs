(ns rumext.examples.local-state
  (:require [rumext.core :as rum :refer-macros [defcs]]
            [rumext.examples.util :as util]))

;; Local component state

(defcs local-state
  {:mixins [(rum/local 0)]}
  [state title]
  (let [*count (::rum/local state)]
    [:div
     {:style {"-webkit-user-select" "none"
              "cursor" "pointer"}
      :on-click (fn [_] (swap! *count inc)) }
     title ": " @*count]))

(defn mount! [el]
  (rum/mount (local-state "Clicks count") el))
