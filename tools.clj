(require '[clojure.java.shell :as shell])
(require '[figwheel.main.api :as figwheel])

(require '[cljs.build.api :as api]
         '[cljs.repl :as repl]
         '[cljs.repl.node :as node])

(require '[badigeon.jar])
(require '[badigeon.deploy])

(require '[rebel-readline.core]
         '[rebel-readline.clojure.main]
         '[rebel-readline.clojure.line-reader]
         '[rebel-readline.clojure.service.local]
         '[rebel-readline.cljs.service.local]
         '[rebel-readline.cljs.repl])

(defmulti task first)

(defmethod task :default
  [args]
  (let [all-tasks  (-> task methods (dissoc :default) keys sort)
        interposed (->> all-tasks (interpose ", ") (apply str))]
    (println "Unknown or missing task. Choose one of:" interposed)
    (System/exit 1)))

(defmethod task "repl"
  [args]
  (rebel-readline.core/with-line-reader
    (rebel-readline.clojure.line-reader/create
     (rebel-readline.clojure.service.local/create))
    (clojure.main/repl
     :prompt (fn []) ;; prompt is handled by line-reader
     :read (rebel-readline.clojure.main/create-repl-read))))

(defmethod task "node:repl"
  [args]
  (rebel-readline.core/with-line-reader
    (rebel-readline.clojure.line-reader/create
     (rebel-readline.cljs.service.local/create))
    (cljs.repl/repl
     (node/repl-env)
     :prompt (fn []) ;; prompt is handled by line-reader
     :read (rebel-readline.cljs.repl/create-repl-read)
     :output-dir "out/repl"
     :cache-analysis false)))

(def build-options
  {:main 'rumext.examples.core
   :output-to "target/public/main.js"
   :source-map "target/public/main.js.map"
   :output-dir "target/public/main"
   :pretty-print false
   :pseudo-names false
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
   {:id "dev" :options (assoc build-options :source-map true)}))

(defmethod task "jar"
  [args]
  (badigeon.jar/jar 'funcool/rumext
                    {:mvn/version "2.0.0-SNAPSHOT"}
                    {:out-path "target/rumext.jar"
                     :mvn/repos '{"clojars" {:url "https://repo.clojars.org/"}}
                     :allow-all-dependencies? false}))

(defmethod task "deploy"
  [args]
  (let [artifacts [{:file-path "target/rumext.jar" :extension "jar"}
                   {:file-path "pom.xml" :extension "pom"}]]
    (badigeon.deploy/deploy
     'funcool/rumext "2.0.0-SNAPSHOT"
     artifacts
     {:id "clojars" :url "https://repo.clojars.org/"}
     {:allow-unsigned? true})))

(defmethod task "build-and-deploy"
  [args]
  (task ["jar"])
  (task ["deploy"]))


;;; Build script entrypoint. This should be the last expression.

(task *command-line-args*)
