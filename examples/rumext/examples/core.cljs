(ns rumext.examples.core
  (:require [clojure.string :as str]
            [rumext.core :as mx]
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
            ))

(enable-console-print!)

(binary-clock/mount! (util/el "binary-clock"))
(timer-static/mount! (util/el "timer-static"))
(timer-reactive/mount! (util/el "timer-reactive"))

(local-state/mount! (util/el "local-state-1")
                    (util/el "local-state-2"))

(refs/mount! (util/el "refs"))
(controls/mount! (util/el "controls"))
(board-reactive/mount! (util/el "board-reactive"))
(portals/mount! (util/el "portals"))
;; (errors/mount! (util/el "errors"))



