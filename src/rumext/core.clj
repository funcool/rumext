;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>

(ns rumext.core
  (:require [hicada.compiler :as hc]
            [rumext.alpha :as ra]))

(defn parse-defc
  [args]
  (loop [r {}
         s 0
         v (first args)
         n (rest args)]
    (case s
      0 (if (symbol? v)
          (recur (assoc r :name v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid macro definition: expected component name." {})))
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
          (throw (ex-info "Invalid macro definition: expected component args vector" {})))
      4 (let [sym (:name r)
              args (:args r)
              func (if (map? v)
                     `(fn ~args ~v (ra/html (do ~@n)))
                     `(fn ~args (ra/html (do ~@(cons v n)))))]
          [func (:doc r) (:mixins r) sym]))))

(defmacro defc
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    (if (empty? mixins)
      `(def ~cname ~doc (rumext.core/build-legacy-fn-ctor ~render ~(str cname)))
      `(def ~cname ~doc (rumext.core/build-legacy-lazy-ctor rumext.core/build-defc ~render ~mixins ~(str cname))))))

(defmacro defcs
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/build-legacy-lazy-ctor rumext.core/build-defcs ~render ~mixins ~(str cname)))))

(defmacro defcc
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/build-legacy-lazy-ctor rumext.core/build-defcc ~render ~mixins ~(str cname)))))
