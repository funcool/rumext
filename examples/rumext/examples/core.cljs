(ns rumext.examples.core
  (:require [clojure.string :as str]
            [goog.dom :as dom]
            [rumext.core :as mx]
            [rumext.alpha :as mf]
            [rumext.examples.util :as util]
            [rumext.examples.binary-clock :as binary-clock]
            [rumext.examples.timer-static :as timer-static]
            [rumext.examples.timer-reactive :as timer-reactive]
            [rumext.examples.local-state :as local-state]
            [rumext.examples.refs :as refs]
            [rumext.examples.controls :as controls]
            [rumext.examples.errors :as errors]
            [rumext.examples.board-reactive :as board-reactive]
            [rumext.examples.portals :as portals]
            [rumext.examples.bench]
            ))

(enable-console-print!)

(binary-clock/mount! (dom/getElement "binary-clock"))
(timer-static/mount! (dom/getElement "timer-static"))
(timer-reactive/mount! (dom/getElement "timer-reactive"))

(local-state/mount! (dom/getElement "local-state"))

(refs/mount! (dom/getElement "refs"))
(controls/mount! (dom/getElement "controls"))
(board-reactive/mount! (dom/getElement "board-reactive"))
(portals/mount! (dom/getElement "portals"))
;; (errors/mount! (util/el "errors"))
