(defproject funcool/rumext "1.1.0"
  :description "A collection of macros and helpers for rum an sablono (used in uxbox)."
  :url "https://github.com/funcool/rumext"

  :license {:name "MPL 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.495" :scope "provided"]]

  :deploy-repositories {"releases" :clojars
                        "snapshots" :clojars}
  :source-paths ["src"]
  :test-paths ["test"]
  :jar-exclusions [#"\.swp|\.swo|user.clj"])

