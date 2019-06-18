;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>

(ns rumext.core
  (:require [rum.core :as rum]
            [hicada.compiler :as hc]))

(defmacro html
  [body]
  (let [opts {:create-element 'js/React.createElement
              :rewrite-for? true
              :array-children? false}]
    (-> body (hicada.compiler/compile opts {} &env))))

(defmethod hc/compile-form "letfn"
  [[_ bindings & body]]
  `(letfn ~bindings ~@(butlast body) ~(hc/emitter (last body))))

(defmethod hc/compile-form "when-let"
  [[_ bindings & body]]
  `(when-let ~bindings ~@(butlast body) ~(hc/emitter (last body))))

(defmethod hc/compile-form "if-let"
  [[_ bindings & body]]
  `(if-let ~bindings ~@(doall (for [x body] (hc/emitter x)))))

(defn parse-defc
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
                     `(fn ~args ~v (html (do ~@n)))
                     `(fn ~args (html (do ~@(cons v n)))))]
          [func (:doc r) (:mixins r) sym]))))

(defmacro defc
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/component rum/build-defc ~render ~mixins ~(str cname)))))

(defmacro defcs
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/component rum/build-defcs ~render ~mixins ~(str cname)))))
