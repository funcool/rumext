(ns rumext.examples.local-state
  (:require [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

(mf/def local-state
  :mixins [(mf/local 0)]
  :render
  (fn [own {:keys [title] :as props}]
    (let [count (::mf/local own)]
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
  (mf/mount (local-state {:title "Clicks count"}) el1)
  (mf/mount (mf/element local-state-fn {:title "(fn) Clicks count"}) el2))


