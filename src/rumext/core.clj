;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>

(ns rumext.core
  (:require [hicada.compiler :as hc]))

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

(defmethod hc/compile-form "fn"
  [[_ params & body]]
  `(fn ~params ~@(butlast body) ~(hc/emitter (last body))))

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
                     `(fn ~args ~v (html (do ~@n)))
                     `(fn ~args (html (do ~@(cons v n)))))]
          [func (:doc r) (:mixins r) sym]))))

(defn parse-def
  [& {:keys [render mixins desc]
      :or {mixins [] desc ""}
      :as params}]
  (let [spec (dissoc params :mixins :render :desc)
        mixins (if (empty? spec)
                 mixins
                 (conj mixins spec))]
    [`(html ~render) desc mixins]))

(defmacro def
  [cname & args]
  (let [[render doc mixins] (apply parse-def args)]
    `(def ~cname ~doc (rumext.core/build-lazy-ctor
                       rumext.core/build-elem-ctor ~render ~mixins ~(str cname)))))

(defmacro defc
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    (if (empty? mixins)
      `(def ~cname ~doc (rumext.core/build-fn-ctor ~render ~(str cname)))
      `(def ~cname ~doc (rumext.core/build-lazy-ctor rumext.core/build-defc ~render ~mixins ~(str cname))))))

(defmacro defcs
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/build-lazy-ctor rumext.core/build-defcs ~render ~mixins ~(str cname)))))

(defmacro defcc
  [& args]
  (let [[render doc mixins cname] (parse-defc args)]
    `(def ~cname ~doc (rumext.core/build-lazy-ctor rumext.core/build-defcc ~render ~mixins ~(str cname)))))
