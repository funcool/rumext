;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2020 Andrey Antukh <niwi@niwi.nz>

(ns ^:no-doc rumext.v2.util
  "Runtime helpers"
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [cljs-bean.core :refer [bean]]))

(defn obj->map
  "Convert shallowly an js object to cljs map."
  [obj]
  (let [keys (.keys js/Object obj)
        len  (alength keys)]
    (loop [i 0
           r (transient {})]
      (if (< i len)
        (let [key (aget keys i)]
          (recur (unchecked-inc i)
                 (assoc! r (keyword key) (unchecked-get obj key))))
        (persistent! r)))))

(defn plain-object?
  ^boolean
  [o]
  (and (some? o)
       (identical? (.getPrototypeOf js/Object o)
                   (.-prototype js/Object))))

(defn map->obj
  [o]
  (cond
    (plain-object? o)
    o

    (map? o)
    (let [m #js {}]
      (run! (fn [[k v]] (unchecked-set m (name k) v)) o)
      m)

    :else
    (throw (ex-info "unable to create obj" {:data o}))))

(defn wrap-props
  [props]
  (cond
    (object? props) (obj->map props)
    (map? props)    props
    (nil? props)    {}
    :else (throw (ex-info "Unexpected props" {:props props}))))

(defn props-equals?
  [eq? new-props old-props]
  (let [old-keys     (.keys js/Object old-props)
        new-keys     (.keys js/Object new-props)
        old-keys-len (alength old-keys)
        new-keys-len (alength new-keys)]
    (if (identical? old-keys-len new-keys-len)
      (loop [idx (int 0)]
        (if (< idx new-keys-len)
          (let [key (aget new-keys idx)
                new-val (unchecked-get new-props key)
                old-val (unchecked-get old-props key)]
            (if ^boolean (eq? new-val old-val)
              (recur (inc idx))
              false))
          true))
      false)))

(defn symbol-for
  [v]
  (.for js/Symbol v))

(defn ^:no-doc validator
  [schema]
  (let [validator (delay (m/validator schema))
        explainer (delay (m/explainer schema))]
    (fn [props]
      (let [props    (bean props)
            validate (deref validator)]
        (when-not ^boolean (^function validate props)
          (let [explainer (deref explainer)
                explain   (^function explainer props)
                explain   (me/humanize explain)]
            (reduce-kv (fn [result k v]
                         (assoc result k (peek v)))
                       explain
                       explain)))))))
