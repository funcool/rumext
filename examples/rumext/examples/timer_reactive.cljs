(ns rumext.examples.timer-reactive
  (:require [goog.dom :as dom]
            [rumext.alpha :as mf]
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
  [{:keys [ts] :as props}]
  [:div "Timer (props)" ": "
   [:span {:style {:color @util/*color}}
    (util/format-time ts)]])

(defonce root1 (mf/create-root (dom/getElement "timer1")))
(defonce root2 (mf/create-root (dom/getElement "timer2")))

(defn mount! []
  (mf/mount root1 (mf/element timer1))
  (mf/mount root2 (mf/element timer2 {:ts @util/*clock}))
  (add-watch util/*clock :timer-static
             (fn [_ _ _ ts]
               (mf/mount root2 (mf/element timer2 {:ts ts})))))




