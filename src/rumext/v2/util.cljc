;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2020 Andrey Antukh <niwi@niwi.nz>

(ns ^:no-doc rumext.v2.util
  "Runtime helpers"
  (:require
   #?(:cljs [cljs-bean.core :refer [bean]])
   [cuerdas.core :as str]
   [malli.core :as m]
   [malli.error :as me]))

(defn ident->key
  [k]
  (let [nword (if (string? k) k (name k))]
    (if (nil? (str/index-of nword "-"))
      nword
      (let [[first-word & words] (str/split nword #"-")
            vendor? (str/starts-with? nword "-")
            result  (-> (map str/capital words)
                        (conj first-word)
                        str/join)]
        (if (str/starts-with? nword "-")
          (str/capital result)
          result)))))

;; (defn- transform-prop-key
;;   [s]
;;   (let [result (js* "~{}.replace(\":\", \"-\").replace(/-./g, x=>x[1].toUpperCase())", s)]
;;     (if ^boolean (gstr/startsWith s "-")
;;       (gstr/capitalize result)
;;       result)))


(defn ident->prop
  "Compiles a keyword or symbol to string using react prop naming
  convention"
  [k]
  (let [nword (name k)]
    (cond
      (= "class" nword) "className"
      (= "for" nword) "htmlFor"
      (str/starts-with? nword "--") nword
      (str/starts-with? nword "data-") nword
      (str/starts-with? nword "aria-") nword
      :else
      (ident->key nword))))

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
   (defn map->props
     ([o] (map->props o false))
     ([o recursive?]
      (let [level (if (true? recursive?) 1 recursive?)]
        (reduce-kv (fn [res k v]
                     (let [v (if (keyword? v) (name v) v)
                           k (cond
                               (string? k)  k
                               (keyword? k) (if (and (int? level) (not= 1 level))
                                              (ident->key k)
                                              (ident->prop k))
                               :else        nil)]

                       (when (some? k)
                         (let [v (cond
                                   (and (= k "style") (map? v))
                                   (map->props v true)

                                   (and (int? level) (map? v))
                                   (map->props v (inc level))

                                   :else
                                   v)]
                           (unchecked-set res k v)))

                       res))
                   #js {}
                   o)))))

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
