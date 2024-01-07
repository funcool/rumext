(ns rumext.examples.timer-reactive
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]
   [rumext.examples.util :as util]))

(defonce components (atom {}))

(mf/defc timer1
  {::mf/register-on components
   ::mf/forward-ref true}
  [props ref]
  (let [ts (mf/deref util/*clock)]
    [:div "Timer (deref)" ": "
     [:span {:style {:color @util/*color}}
      (util/format-time ts)]]))

(mf/defc timer2
  {::mf/wrap [#(mf/throttle % 1000)]}
  [{:keys [ts] :as props}]
  [:div "Timer (props)" ": "
   [:span {:style {:color @util/*color}}
    (util/format-time ts)]])

(def root1 (mf/create-root (dom/getElement "timer1")))
(def root2 (mf/create-root (dom/getElement "timer2")))

(defn mount! []
  (mf/render! root1 (mf/jsx timer1 {}))
  (mf/render! root2 (mf/jsx timer2 #js  {:ts @util/*clock}))
  (add-watch util/*clock :timer-static
             (fn [_ _ _ ts]
               (mf/render! root2 (mf/jsx timer2 #js {:ts ts})))))
