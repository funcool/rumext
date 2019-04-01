(ns rumext.examples.core
  (:require [clojure.string :as str]
            [rumext.core :as r]
            [rumext.examples.util :as util]
            [rumext.examples.binary-clock :as binary-clock]
            [rumext.examples.timer-static :as timer-static]
            [rumext.examples.local-state :as local-state]
            ))

(enable-console-print!)

(binary-clock/mount! (util/el "binary-clock"))
(timer-static/mount! (util/el "timer-static"))
(local-state/mount! (util/el "local-state"))

;; Start clock ticking
(defn tick []
  (reset! util/*clock (.getTime (js/Date.))))

(defonce sem (js/setInterval tick @util/*speed))
