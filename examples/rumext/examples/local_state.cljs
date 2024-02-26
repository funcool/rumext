(ns rumext.examples.local-state
  (:require
   [goog.dom :as dom]
   [malli.core :as m]
   [rumext.v2 :as mf]
   [rumext.examples.util :as util]))

(def schema:label
  [:map {:title "label:props"}
   [:on-click {:optional true} fn?]
   [:title string?]
   [:n number?]])

(mf/defc label
  {::mf/memo true
   ::mf/props :react
   ::mf/schema schema:label
   }
  [{:keys [title n] :as props :rest others}]
  (js/console.log "label:props" props)
  (js/console.log "label:others" others)
  (let [props (mf/spread-props others {:class "my-label"})]
    [:> :div props
     [:span title ": " n]]))

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
     (let [{:keys [title n]} (:counter1 @local)]
       [:> label {:n n :title title :data-foobar 1 :on-click identity :id "foobar"}])
     #_(let [{:keys [title n]} (:counter2 @local)]
       [:> label {:title title :n n :on-click identity}])
     [:button {:on-click #(swap! local update-in [:counter1 :n] inc)} "Increment Counter 1"]
     [:button {:on-click #(swap! local update-in [:counter2 :n] inc)} "Increment Counter 2"]]))

(defonce root
  (mf/create-root (dom/getElement "local-state-1")))

(defn mount! []
  (mf/render! root (mf/element local-state #js {:title "Clicks count"})))
