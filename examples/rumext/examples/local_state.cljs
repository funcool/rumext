(ns rumext.examples.local-state
  (:require [goog.dom :as dom]
            [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

(mf/def label
  :render
  (fn [own {:keys [title n] :as props}]
    [:div
     [:span title ": " n]]))

(mf/def local-state
  :mixins [(mf/local 0)]
  :render
  (fn [own {:keys [title] :as props}]
    (let [count (::mf/local own)]
      [:section
       [:div
        {:style {"-webkit-user-select" "none"
                 "cursor" "pointer"}
         :on-click (fn [_] (swap! count inc)) }

        [:& label {:title "Counter1" :n @count :bar #{:baz :rrr}}]
        (label {:title "Counter2" :n @count :bar #{:baz :rrr}})]])))

(mf/defc local-state-fn
  [{:keys [title] :as props}]
  (let [count (mf/use-state 0)]
    [:div
     {:style {"-webkit-user-select" "none"
              "cursor" "pointer"}
      :on-click (fn [_] (swap! count inc)) }
     title ": " @count]))

(defn mount! [parent]
  (let [el1 (dom/getElement "local-state-1" parent)
        el2 (dom/getElement "local-state-2" parent)]
    (mf/mount (mf/element local-state {:title "Clicks count"}) el1)
    (mf/mount (mf/element local-state-fn {:title "(fn) Clicks count"}) el2)))


