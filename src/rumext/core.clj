;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2017 Andrey Antukh <niwi@niwi.nz>

(ns rumext.core
  (:refer-clojure :exclude [doseq])
  (:require [rum.core :as rum]
            [sablono.compiler :as s]))

(defn- parse-defc
  [args]
  (loop [r {}
         s 0
         v (first args)
         n (rest args)]
    (case s
      0 (if (symbol? v)
          (recur (assoc r :name v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid" {})))
      1 (if (string? v)
          (recur (assoc r :doc v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      2 (if (map? v)
          (if-let [mixins (:mixins v)]
            (let [spec (dissoc v :mixins)
                  mixins (if (empty? spec)
                           mixins
                           (conj mixins spec))]
              (recur (assoc r :mixins mixins) (inc s) (first n) (rest n)))
            (recur (assoc r :mixins [v]) (inc s) (first n) (rest n)))
          (recur r (inc s) v n))
      3 (if (vector? v)
          (recur (assoc r :args v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid" {})))
      4 (let [sym (:name r)
              args (:args r)
              func (if (map? v)
                     `(fn ~args ~v ~(s/compile-html `(do ~@n)))
                     `(fn ~args ~(s/compile-html `(do ~@(cons v n)))))]
          [func (:doc r) (:mixins r) sym]))))

(defmacro defc
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/lazy-component rum/build-defc ~render ~mixins ~(str cname)))))

(defmacro defcs
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/lazy-component rum/build-defcs ~render ~mixins ~(str cname)))))


(.addMethod @(var s/compile-form) "letfn"
            (fn [[_ bindings & body]]
              `(letfn ~bindings ~@(butlast body) ~(s/compile-html (last body)))))

(.addMethod @(var s/compile-form) "when"
            (fn
              [[_ bindings & body]]
              `(when ~bindings ~@(for [x body] (s/compile-html x)))))

(.addMethod @(var s/compile-form) "when-not"
            (fn
              [[_ bindings & body]]
              `(when-not ~bindings ~@(for [x body] (s/compile-html x)))))

(.addMethod @(var s/compile-form) "if-not"
            (fn
              [[_ bindings & body]]
              `(if-not ~bindings ~@(for [x body] (s/compile-html x)))))

(defmacro doseq
  [[item coll] & body]
  `(let [coll# ~coll
         result# (cljs.core/array)]
     (loop [xs# coll#
            idx# 0]
       (when-some [~item (first xs#)]
         (.push result# ~(s/compile-html `(do ~@body)))
         (recur (next xs#) (inc idx#))))
     (seq result#)))
