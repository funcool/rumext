;; This Source Code Form is subject to the terms of the Eclipse Public
;; License - v 1.0
;; Copyright (c) 2019 Andrey Antukh <niwi@niwi.nz>

(ns rumext.func
  (:require [rumext.core :as mx]))

(defn parse-defc
  [args]
  (loop [r {}
         s 0
         v (first args)
         n (rest args)]
    (case s
      0 (if (symbol? v)
          (recur (assoc r :name (str v)) (inc s) (first n) (rest n))
          (recur (assoc r :name "anonymous") (inc s) (first n) (rest n)))
      1 (if (string? v)
          (recur (assoc r :doc v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      2 (if (map? v)
          (recur (assoc r :metadata v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      3 (if (vector? v)
          (recur (assoc r :args v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid macro definition: expected component args vector" {})))
      4 (let [sym (:name r)
              args (:args r)
              func (if (map? v)
                     `(fn ~args ~v (mx/html (do ~@n)))
                     `(fn ~args (mx/html (do ~@(cons v n)))))]
          [func (:doc r) (:metadata r) sym]))))

(defmacro fnc
  [& args]
  (let [[render doc metadata cname] (parse-defc args)]
    `(rumext.func/build-fn-ctor ~render ~cname ~metadata)))

(defmacro defnc
  [& args]
  (let [[render doc metadata cname] (parse-defc args)]
    `(def ~(symbol cname) ~doc (rumext.func/build-fn-ctor ~render ~cname ~metadata))))
