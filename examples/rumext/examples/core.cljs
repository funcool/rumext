(ns rumext.examples.core
  (:require [clojure.string :as str]
            [goog.dom :as dom]
            [rumext.alpha :as mf]
            [rumext.examples.util :as util]
            [rumext.examples.binary-clock :as binary-clock]
            [rumext.examples.timer-reactive :as timer-reactive]
            [rumext.examples.local-state :as local-state]
            [rumext.examples.refs :as refs]
            [rumext.examples.controls :as controls]
            ;; [rumext.examples.errors :as errors]
            [rumext.examples.board :as board]
            ;; [rumext.examples.portals :as portals]
            ))

(enable-console-print!)

(binary-clock/mount!)
(timer-reactive/mount!)
(local-state/mount!)

(refs/mount!)
(controls/mount!)
(board/mount!)

(defn main
  [& args]
  (js/console.log "main" args))
