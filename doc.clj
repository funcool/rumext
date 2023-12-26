(require '[codox.main :as codox])

(codox/generate-docs
 {:output-path "doc/dist/latest"
  :metadata {:doc/format :markdown}
  :language :clojurescript
  :name "funcool/rumext"
  :themes [:rdash]
  :source-paths ["src"]
  :namespaces [#"^rumext\."]
  :source-uri "https://github.com/funcool/rumext/blob/master/{filepath}#L{line}"})
