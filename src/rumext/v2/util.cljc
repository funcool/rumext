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
   [clojure.string :as str]
   #?(:cljs [cljs-bean.core :refer [bean]])))

(defn compile-prop-key
  "Compiles a key to a react compatible key (eg: camelCase)"
  [k]
  (if (or (keyword? k) (symbol? k))
    (let [nword (name k)]
      (cond
        (= "class" nword) "className"
        (= "for" nword) "htmlFor"
        (str/starts-with? nword "--") nword
        (str/starts-with? nword "data-") nword
        (str/starts-with? nword "aria-") nword
        :else
        (let [[first-word & words] (str/split nword #"-")]
          (if (empty? words)
            nword
            (-> (map str/capitalize words)
                (conj first-word)
                str/join)))))
    k))

#?(:cljs
   (defn obj->map
     [obj]
     (let [keys (.keys js/Object obj)
           len  (alength keys)]
       (loop [i 0
              r (transient {})]
         (if (< i len)
           (let [key (aget keys i)]
             (recur (unchecked-inc i)
                    (assoc! r (keyword key) (unchecked-get obj key))))
           (persistent! r))))))

#?(:cljs
   (defn plain-object?
     ^boolean
     [o]
     (and (some? o)
          (identical? (.getPrototypeOf js/Object o)
                      (.-prototype js/Object)))))

#?(:cljs
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
       (throw (ex-info "unable to create obj" {:data o})))))

#?(:cljs
   (defn wrap-props
     [props]
     (cond
       (object? props) (obj->map props)
       (map? props)    props
       (nil? props)    {}
       :else (throw (ex-info "Unexpected props" {:props props})))))

#?(:cljs
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
         false))))

#?(:cljs
   (defn symbol-for
     [v]
     (.for js/Symbol v)))

#?(:cljs
   (defn- default-key->prop [x]
     (when (keyword? x)
       (.-fqn x))))

#?(:cljs
   (defn ^:no-doc validator
     [schema react-props?]
     (let [validator (delay (m/validator schema))
           explainer (delay (m/explainer schema))]
       (fn [props]
         (let [props    (bean props
                              :prop->key keyword
                              :key->prop (if react-props?
                                           compile-prop-key
                                           default-key->prop))

               validate (deref validator)]
           (when-not ^boolean (^function validate props)
             (let [explainer (deref explainer)
                   explain   (^function explainer props)
                   explain   (me/humanize explain)]
               (reduce-kv (fn [result k v]
                            (assoc result k (peek v)))
                          explain
                          explain))))))))
