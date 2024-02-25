(ns rumext.examples.local-state
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]
   [rumext.examples.util :as util]))

(mf/defc label
  {::mf/memo true
   ::mf/props :obj
   ::mf/expect #{:state}}
  [{:keys [state] :as props :rest others}]
  (let [{:keys [title n]} state
        props (mf/spread-props {:class "baar"}
                               :data-test "1")
        ]
    (js/console.log props)
    (js/console.log others)
    [:div {:class "foobar"}
     [:span title ": " n]]))

(mf/defc label*
  {::mf/wrap [#(mf/memo' % (mf/check-props ["title" "n"]))]}
  [{:keys [title n on-click data-foo] :as props}]
  [:div
   [:span title ": " n]])

(mf/defc local-state
  "test docstring"
  {::mf/memo true
   ::mf/props :obj}
  [{:keys [title]}]
  (let [local (mf/use-state
               #(-> {:counter1 {:title "Counter 1"
                                :n 0}
                     :counter2 {:title "Counter 2"
                                :n 0}}))]
    [:section {:class "counters"}
     [:hr]
     [:& label {:state (:counter1 @local) :data-foobar 1 :on-click identity :id :foobar}]
     (let [{:keys [title n]} (:counter2 @local)]
       [:> label* {:title title :n n :on-click identity}])
     [:button {:on-click #(swap! local update-in [:counter1 :n] inc)} "Increment Counter 1"]
     [:button {:on-click #(swap! local update-in [:counter2 :n] inc)} "Increment Counter 2"]]))

(def root (mf/create-root (dom/getElement "local-state-1")))

(defn mount! []
  (mf/render! root (mf/element local-state #js {:title "Clicks count"})))
