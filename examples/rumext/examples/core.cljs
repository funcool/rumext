(ns rumext.examples.core
  (:require [clojure.string :as str]
            [rumext.core :as r]
            [rumext.examples.util :as util]
            [rumext.examples.binary-clock :as binary-clock]
            [rumext.examples.timer-static :as timer-static]
            [rumext.examples.local-state :as local-state]
            [rumext.examples.refs :as refs]
            [rumext.examples.controls :as controls]
            [rumext.examples.errors :as errors]
            ))

(enable-console-print!)

(binary-clock/mount! (util/el "binary-clock"))
(timer-static/mount! (util/el "timer-static"))
(local-state/mount! (util/el "local-state"))
(refs/mount! (util/el "refs"))
(controls/mount! (util/el "controls"))
;; (errors/mount! (util/el "errors"))



