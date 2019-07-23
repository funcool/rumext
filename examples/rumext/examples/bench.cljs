(ns rumext.examples.bench
  (:require
   [goog.dom :as dom]
   [rumext.core :as mx]
   [rumext.alpha :as mf]))

(mx/defc legacy-component
  {:static-properties {:foobar 1}}
  [a b c d e]
  [:div
   [:span a]
   [:span b]
   [:span c]
   [:span d]
   [:span e]])

(mf/def class-component
  :static-properties {:foobar 1}
  :render
  (fn [own {:keys [a b c d e f]}]
    [:div
     [:span a]
     [:span b]
     [:span c]
     [:span d]
     [:span e]]))

(mf/defc fn-component
  [{:keys [a b c d e f]}]
  [:div
   [:span a]
   [:span b]
   [:span c]
   [:span d]
   [:span e]])

(defn ^:export bench
  []
  (simple-benchmark [el (dom/getElement "inputs")]
    (mx/mount (mf/element fn-component {:a 1 :b 2 :c 3 :d 4 :e 5}) el)
    10000)

  (simple-benchmark [el (dom/getElement "inputs")]
    (mx/mount (class-component {:a 1 :b 2 :c 3 :d 4 :e 5}) el)
    10000)
  (simple-benchmark [el (dom/getElement "inputs")]
    (mx/mount (mf/element class-component {:a 1 :b 2 :c 3 :d 4 :e 5}) el)
    10000)

  (simple-benchmark [el (dom/getElement "inputs")]
    (mx/mount (legacy-component 1 2 3 4 5) el)
    10000)
  )

(defn ^:export double-bench
  []
  (println "==> first pass")
  (bench)
  (println "==> second pass")
  (bench))
