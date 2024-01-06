(ns rumext.examples.core
  (:require
   [rumext.examples.binary-clock :as binary-clock]
   [rumext.examples.timer-reactive :as timer-reactive]
   [rumext.examples.local-state :as local-state]
   [rumext.examples.refs :as refs]
   [rumext.examples.controls :as controls]
   [rumext.examples.board :as board]
   ;; [rumext.examples.errors :as errors]
   ))

;; (enable-console-print!)
(local-state/mount!)

(binary-clock/mount!)
(timer-reactive/mount!)

(refs/mount!)
(controls/mount!)
(board/mount!)

(defn main
  [& args]
  (js/console.log "main" args))
