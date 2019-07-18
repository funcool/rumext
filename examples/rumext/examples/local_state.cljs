(ns rumext.examples.local-state
  (:require [rumext.core :as mx]
            [rumext.func :as mf]
            [rumext.examples.util :as util]))

(mx/def local-state
  :mixins [(mx/local 0)]
  :render
  (fn [own {:keys [title] :as props}]
    (let [count (::mx/local own)]
      [:div
       {:style {"-webkit-user-select" "none"
                "cursor" "pointer"}
        :on-click (fn [_] (swap! count inc)) }
       title ": " @count])))

(mf/defc local-state-fn
  [{:keys [title] :as props}]
  (let [count (mf/use-state 0)]
    [:div
     {:style {"-webkit-user-select" "none"
              "cursor" "pointer"}
      :on-click (fn [_] (swap! count inc)) }
     title ": " @count]))

(defn mount! [el1 el2]
  (mx/mount (local-state {:title "Clicks count"}) el1)
  (mx/mount (mf/element local-state-fn {:title "(fn) Clicks count"}) el2))


