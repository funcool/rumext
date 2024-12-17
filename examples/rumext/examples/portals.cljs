(ns rumext.examples.portals
  (:require
   [rumext.v2 :as mf]
   [goog.dom :as dom]))

(mf/defc portal*
  [{:keys [state]}]
  [:div {:on-click (fn [_] (swap! state inc))
         :style { :user-select "none", :cursor "pointer" }}
   "[ PORTAL Clicks: " @state " ]"])

(mf/defc portals*
  []
  (let [state (mf/use-state 0)]
    [:div {:on-click (fn [_] (swap! state inc))
           :style { :user-select "none", :cursor "pointer" }}
     "[ ROOT Clicks: " @state " ]"
     (mf/portal
      (mf/html [:> portal* {:state state}])
      (dom/getElement "portal-off-root"))]))

(defonce root
  (mf/create-root (dom/getElement "portals")))

(defn ^:after-load mount! []
  (mf/render! root (mf/element portals*)))
