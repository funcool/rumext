 (ns rumext.examples.local-state
  (:require [goog.dom :as dom]
            [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

;; (mf/defc label
;;   {:wrap [mf/wrap-memo]}
;;   [{:keys [state] :as props}]
;;   ;; (prn "label" props)
;;   (let [{:keys [title n]} state]
;;     [:div
;;      [:span title ": " n]]))

(def label
  (mf/fnc label [{:keys [state] :as props}]
    (let [{:keys [title n]} state]
      [:*
       [:div
        [:span title ": " n]]])))


(mf/defc local-state
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


(defn mount! []
  (mf/mount (mf/element local-state {:title "Clicks count"})
            (dom/getElement "local-state-1")))


