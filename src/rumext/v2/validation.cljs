;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2020 Andrey Antukh <niwi@niwi.nz>

(ns ^:no-doc rumext.v2.validation
  "Runtime helpers"
  (:require
   [cljs-bean.core :as bean]
   [cuerdas.core :as str]
   [rumext.v2.util :as util]
   [malli.core :as m]
   [malli.transform :as mt]
   [malli.error :as me]))

(def default-transformer mt/json-transformer)

(defn process-explain-kv
  [prefix result k v]
  (let [nm (if (keyword? k)
             (name k)
             (str k))
        pk (if prefix
             (str prefix "." nm)
             nm)]
    (cond
      (and (vector? v) (every? vector? v))
      (let [data (into {} (map-indexed vector) v)]
        (reduce-kv (partial process-explain-kv pk) result data))

      (and (vector? v) (every? map? v))
      (let [gdata (into {} (comp
                            (map :malli/error)
                            (map-indexed vector)
                            (filter second))
                        v)
            ndata (into {} (comp
                            (map #(dissoc % :malli/error))
                            (map-indexed vector))
                        v)

            result (reduce-kv (partial process-explain-kv pk) result gdata)
            result (reduce-kv (partial process-explain-kv pk) result ndata)]

        result)

      (and (vector? v) (every? string? v))
      (assoc result pk (peek v))

      (map? v)
      (reduce-kv (partial process-explain-kv pk) result v)

      :else
      result)))

(defn- camel-key->prop
  [k]
  (if (or (keyword? k) (symbol? k))
    (str/camel k)
    (str k)))

(defn- react-prop->lisp-key
  [k]
  (if (and (string? k) (not (str/includes? k "/")))
    (cond
      (= k "htmlFor") :for
      (= k "className") :class
      :else
      (-> k str/kebab keyword))
    k))

(defn- prop->lisp-key
  [k]
  (if (and (string? k) (not (str/includes? k "/")))
    (-> k str/kebab keyword)
    k))

(defn- react-key->prop
  [x]
  (when (simple-keyword? x)
    (util/ident->prop x)))

(defn- identity-key->prop
  [x]
  (when (keyword? x)
    (.-fqn ^cljs.core.Keyword x)))

(defn- bean-transform
  [o]
  (when ^boolean (util/plain-object? o)
    (bean/->clj o
                :prop->key prop->lisp-key
                :key->prop camel-key->prop)))

(defn ^:no-doc validator
  [schema react-props?]
  (let [validator (delay (m/validator schema))
        explainer (delay (m/explainer schema))
        decoder   (delay (m/decoder schema default-transformer))]
    (fn [props']

      (let [props    (bean/bean props'
                                :recursive true
                                ;; :transform bean-transform
                                :prop->key react-prop->lisp-key
                                :key->prop (if react-props?
                                             react-key->prop
                                             identity-key->prop))

            props    (@decoder props)
            validate (deref validator)]
        (when-not ^boolean (^function validate props)
          (let [explainer (deref explainer)
                explain   (^function explainer props)
                explain   (me/humanize explain)]
            (reduce-kv (partial process-explain-kv nil) {} explain)))))))
