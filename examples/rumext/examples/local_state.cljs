 (ns rumext.examples.local-state
  (:require [goog.dom :as dom]
            [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

(def label
  (mf/fnc label
    {::mf/wrap [mf/memo]}
    [{:keys [state] :as props}]

    (let [{:keys [title n]} state]
      [:*
       [:div
        [:span title ": " n]]])))


(mf/defc local-state
  "test docstring"
  {::mf/wrap [mf/memo]}
  [{:keys [title] :as props}]
  (let [local (mf/use-state {:counter1 {:title "Counter 1"
                                        :n 0}
                             :counter2 {:title "Counter 2"
                                        :n 0}})]

    [:section {:class "counters"}
     [:& label {:state (:counter1 @local)}]
     [:& label {:state (:counter2 @local)}]
     [:button {:on-click #(swap! local update-in [:counter1 :n] inc)} "Increment Counter 1"]
     [:button {:on-click #(swap! local update-in [:counter2 :n] inc)} "Increment Counter 2"]]))


(defonce root (mf/create-root (dom/getElement "local-state-1")))

(defn mount! []
  ;; (mf/unmount root)
  (mf/mount root (mf/element local-state {:title "Clicks count"})))


