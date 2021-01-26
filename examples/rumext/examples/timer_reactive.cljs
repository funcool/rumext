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

(defn mount! []
  (mf/mount (mf/element timer1)
            (dom/getElement "timer1"))
  (mf/mount (mf/element timer2 {:ts @util/*clock})
            (dom/getElement "timer2"))

  (add-watch util/*clock :timer-static
             (fn [_ _ _ ts]
               (mf/mount (mf/element timer2 {:ts ts})
                         (dom/getElement "timer2")))))




