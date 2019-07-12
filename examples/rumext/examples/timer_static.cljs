(ns rumext.examples.timer-static
  (:require [rumext.core :as rmx :refer-macros [defc]]
            [rumext.examples.util :as util]))

(defc timer-static
  ;; {:mixins [rmx/reactive]
  ;;  :init (fn [state]
  ;;          (prn "init" state)
  ;;          state)
  ;;  :derive-state (fn [state]
  ;;                  (prn "derive-state")
  ;;                  state)
  ;;  :did-mount (fn [state]
  ;;               (prn "did-mount")
  ;;               state)
  ;;  :should-update (fn [lstate nstate]
  ;;                   (prn "should-update" lstate nstate)
  ;;                   true)
  ;;  :make-snapshot (fn [state]
  ;;                   (prn "make-snapshot")
  ;;                   "foobar")
  ;;  :did-update (fn [state snapshot]
  ;;                (prn "did-update" snapshot)
  ;;                state)}
  [label ts]
  ;; (prn "render")
  [:div label ": "
   [:span {:style {:color @util/*color}} (util/format-time ts)]])

(defn mount! [el]
  (rmx/mount (timer-static "Static" @util/*clock) el)
  (add-watch util/*clock :timer-static
             (fn [_ _ _ new-val]
               ;; (prn "****")
               (rmx/mount (timer-static "Static" new-val) el))))
