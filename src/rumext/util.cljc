;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2020 Andrey Antukh <niwi@niwi.nz>

(ns rumext.util
  (:require
   [clojure.string :as str]
   [clojure.set :as set]))

(defn compile-to-js
  [form]
  (cond
    (map? form)
    (when-not (empty? form)
      (let [key-strs (mapv compile-to-js (keys form))
            non-str (remove string? key-strs)
            _ (assert (empty? non-str)
                      (str "Rumext: Props can't be dynamic:"
                           (pr-str non-str) "in: " (pr-str form)))
            kvs-str (->> (mapv #(-> (str \' % "':~{}")) key-strs)
                         (interpose ",")
                         (apply str))]
        (vary-meta
         (list* 'js* (str "{" kvs-str "}") (mapv compile-to-js (vals form)))
         assoc :tag 'object)))

    (vector? form)
    (apply list 'cljs.core/array (mapv compile-to-js form))

    (keyword? form)
    (name form)

    :else form))

(defn compile-to-js*
  [m]
  (when-not (empty? m)
    (let [key-strs (mapv compile-to-js (keys m))
          non-str (remove string? key-strs)
          _ (assert (empty? non-str)
                    (str "Rumext: Props can't be dynamic:"
                         (pr-str non-str) "in: " (pr-str m)))
          kvs-str (->> (mapv #(-> (str \' % "':~{}")) key-strs)
                       (interpose ",")
                       (apply str))]
      (vary-meta
        (list* 'js* (str "{" kvs-str "}") (mapv identity (vals m)))
        assoc :tag 'object))))

#?(:cljs
   (defn obj->map
     "Convert shallowly an js object to cljs map."
     [obj]
     (let [keys (.keys js/Object obj)
           len (alength keys)]
       (loop [i 0
              r (transient {})]
         (if (< i len)
           (let [key (aget keys i)]
             (recur (unchecked-inc i)
                    (assoc! r (keyword key) (unchecked-get obj key))))
           (persistent! r))))))

#?(:cljs
   (defn map->obj
     [o]
     (let [m #js {}]
       (run! (fn [[k v]] (unchecked-set m (name k) v)) o)
       m)))

#?(:cljs
   (defn wrap-props
     [props]
     (cond
       (object? props) (obj->map props)
       (map? props) props
       (nil? props) {}
       :else (throw (ex-info "Unexpected props" {:props props})))))

#?(:cljs
   (defn props-equals?
     [eq? new-props old-props]
     (let [old-keys (.keys js/Object old-props)
           new-keys (.keys js/Object new-props)
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
         false))))

#?(:cljs
   (defn symbol-for
     [v]
     (.for js/Symbol v)))

(defn join-classes-js
  "Joins strings space separated"
  ([] "")
  ([& xs]
   (let [strs (->> (repeat (count xs) "~{}")
                   (interpose ",")
                   (apply str))]
     (list* 'js* (str "[" strs "].join(' ')") xs))))

(defn camel-case
  "Returns camel case version of the key, e.g. :http-equiv becomes :httpEquiv."
  [k]
  (if (or (keyword? k)
          (string? k)
          (symbol? k))
    (let [[first-word & words] (str/split (name k) #"-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        k
        (-> (map str/capitalize words)
            (conj first-word)
            str/join
            keyword)))
    k))

(defn camel-case-keys
  "Recursively transforms all map keys into camel case."
  [m]
  (cond
    (map? m)
    (reduce-kv
      (fn [m k v]
        (assoc m (camel-case k) v))
      {} m)
    ;; React native accepts :style [{:foo-bar ..} other-styles] so camcase those keys:
    (vector? m)
    (mapv camel-case-keys m)
    :else
    m))

(defn element?
  "- is x a vector?
  AND
   - first element is a keyword?"
  [x]
  (and (vector? x) (keyword? (first x))))

(defn unevaluated?
  "True if the expression has not been evaluated.
   - expr is a symbol? OR
   - it's something like (foo bar)"
  [expr]
  (or (symbol? expr)
      (and (seq? expr)
           (not= (first expr) `quote))))

(defn literal?
  "True if x is a literal value that can be rendered as-is."
  [x]
  (and (not (unevaluated? x))
       (or (not (or (vector? x) (map? x)))
           (and (every? literal? x)
                (not (keyword? (first x)))))))

(defn join-classes
  "Join the `classes` with a whitespace."
  [classes]
  (->> (map #(if (string? %) % (seq %)) classes)
       (flatten)
       (remove nil?)
       (str/join " ")))
