(ns rumext.examples.portals
  (:require [rumext.core :as rmx]
            [rumext.examples.util :as util]))


(rmx/defc portal
  [*clicks]
  [:div {:on-click (fn [_] (swap! *clicks inc))
         :style { :user-select "none", :cursor "pointer" }}
   "[ PORTAL Clicks: " @*clicks " ]"])


(rmx/defcs portals
  {:mixins [(rmx/local 0 ::*clicks)]}
  [{*clicks ::*clicks}]
  [:div {:on-click (fn [_] (swap! *clicks inc))
         :style { :user-select "none", :cursor "pointer" }}
   "[ ROOT Clicks: " @*clicks " ]"
   (rmx/portal (portal *clicks) (util/el "portal-off-root"))])


(defn mount! [el]
  (rmx/mount (portals) el))
