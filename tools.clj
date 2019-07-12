(require '[clojure.java.shell :as shell])
(require '[figwheel.main.api :as figwheel])
(require '[cljs.build.api :as api])
;; (require '[rebel-readline.core]
;;          '[rebel-readline.clojure.main]
;;          '[rebel-readline.clojure.line-reader]
;;          '[rebel-readline.clojure.service.local])


(defmulti task first)

(defmethod task :default
  [args]
  (let [all-tasks  (-> task methods (dissoc :default) keys sort)
        interposed (->> all-tasks (interpose ", ") (apply str))]
    (println "Unknown or missing task. Choose one of:" interposed)
    (System/exit 1)))

;; (defmethod task "test"
;;   [[_ exclude]]
;;   (let [tests (ef/find-tests "test")
;;         tests (if (string? exclude)
;;                 (ef/find-tests (symbol exclude))
;;                 tests)]
;;     (ef/run-tests tests
;;                   {:fail-fast? true
;;                    :capture-output? false
;;                    :multithread? false})
;;     (System/exit 1)))

;; (defmethod task "repl"
;;   [args]
;;   (rebel-readline.core/with-line-reader
;;     (rebel-readline.clojure.line-reader/create
;;      (rebel-readline.clojure.service.local/create))
;;     (clojure.main/repl
;;      :prompt (fn []) ;; prompt is handled by line-reader
;;      :read (rebel-readline.clojure.main/create-repl-read))))3

;; (def options
;;   {:main 'rumext.examples.core
;;    :output-to "out/main.js"
;;    :output-dir "out"
;;    :optimizations :none
;;    :pretty-print true
;;    :language-in  :ecmascript5
;;    :language-out :ecmascript5
;;    :verbose true})

(def build-options
  {:main 'rumext.examples.core
   :output-to "target/public/main.js"
   :output-dir "target/public/main"
   :pretty-print true
   :source-map true
   :verbose true})

(def figwheel-options
  {:open-url false
   :load-warninged-code true
   :auto-testing false
   :ring-server-options {:port 9500 :host "0.0.0.0"}
   :watch-dirs ["src" "examples"]})

(defn build
  [optimizations]
  (api/build (api/inputs "src" "examples")
             (cond->  (assoc build-options :optimizations optimizations)
               (= optimizations :none) (assoc :source-map true))))

(defmethod task "build"
  [[_ type]]
  (case type
    (nil "none") (build :none)
    "simple"     (build :simple)
    "advanced"   (build :advanced)
    (do (println "Unknown argument to test task:" type)
        (System/exit 1))))

(defmethod task "figwheel"
  [args]
  (figwheel/start
   figwheel-options
   {:id "dev" :options build-options}))

;;; Build script entrypoint. This should be the last expression.

(task *command-line-args*)
