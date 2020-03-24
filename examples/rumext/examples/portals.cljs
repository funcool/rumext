(ns rumext.examples.portals
  #_(:require [rumext.core :as mx]
            [rumext.examples.util :as util]))

;; (mx/defc portal
;;   [*clicks]
;;   [:div {:on-click (fn [_] (swap! *clicks inc))
;;          :style { :user-select "none", :cursor "pointer" }}
;;    "[ PORTAL Clicks: " @*clicks " ]"])


;; (mx/defcs portals
;;   {:mixins [(mx/local 0 ::*clicks)]}
;;   [{*clicks ::*clicks}]
;;   [:div {:on-click (fn [_] (swap! *clicks inc))
;;          :style { :user-select "none", :cursor "pointer" }}
;;    "[ ROOT Clicks: " @*clicks " ]"
;;    (mx/portal (portal *clicks) (util/el "portal-off-root"))])


;; (defn mount! [el]
;;   (mx/mount (portals) el))
