;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2020 Andrey Antukh <niwi@niwi.nz>

(ns rumext.alpha
  (:require [hicada.compiler :as hc]))

(defn- to-js-map
  "Convert a map into a JavaScript object."
  [m]
  (when-not (empty? m)
    (let [key-strs (mapv hc/to-js (keys m))
          non-str (remove string? key-strs)
          _ (assert (empty? non-str)
                    (str "Hicada: Props can't be dynamic:"
                         (pr-str non-str) "in: " (pr-str m)))
          kvs-str (->> (mapv #(-> (str \' % "':~{}")) key-strs)
                       (interpose ",")
                       (apply str))]
      (vary-meta
        (list* 'js* (str "{" kvs-str "}") (mapv identity (vals m)))
        assoc :tag 'object))))

(create-ns 'rumext.util)

(def handlers
  {:& (fn
        ([_ klass]
         (let [klass klass]
           [klass {} nil]))
        ([_ klass props & children]
         (let [klass klass]
           (if (map? props)
             [klass (to-js-map props) children]
             [klass (list 'rumext.util/map->obj props) children]))))

   :* (fn [_ attrs & children]
        (if (map? attrs)
          ['rumext.alpha/Fragment attrs children]
          ['rumext.alpha/Fragment {} (cons attrs children)]))})

(defmacro html
  [body]
  (let [opts {:create-element 'rumext.alpha/create-element
              :rewrite-for? true
              :array-children? true}]
    (-> body (hicada.compiler/compile opts handlers &env))))

(defmethod hc/compile-form "cond"
  [[_ & clauses]]
  `(cond ~@(doall
            (mapcat
             (fn [[check expr]] [check (hc/compile-html expr)])
             (partition 2 clauses)))))

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
          (recur (assoc r :cname v) (inc s) (first n) (rest n))
          (recur (assoc r :cname (gensym "anonymous-")) (inc s) v n))
      1 (if (string? v)
          (recur (assoc r :doc v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      2 (if (map? v)
          (recur (assoc r :metadata v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      3 (if (vector? v)
          (recur (assoc r :args v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid macro definition: expected component args vector" {})))
      4 {:cname     (:cname r)
         :docs      (str (:doc r))
         :arg-props (first (:args r))
         :arg-rest  (rest (:args r))
         :body      (cons v n)
         :meta      (:metadata r)})))

(defn- prepare-render
  [{:keys [cname meta arg-props arg-rest body] :as ctx}]
  (let [argsym (gensym "arg")
        args   (cons argsym arg-rest)
        fnbody `(fn ~cname [~@(if arg-props args [])]
                  (let [~@(cond
                            (and arg-props (::wrap-props meta true))
                            [arg-props `(rumext.util/wrap-props ~argsym)]

                            (some? arg-props)
                            [arg-props argsym]

                            :else [])]
                    ~@(butlast body)
                    (html ~(last body))))]

    (if (::forward-ref meta)
      `(rumext.alpha/forward-ref ~fnbody)
      fnbody)))

(defmacro fnc
  [& args]
  (let [{:keys [cname meta] :as ctx} (parse-defc args)
        wrap-with (or (::wrap meta)
                      (:wrap meta))
        rfs (gensym "component")]
    `(let [~rfs ~(prepare-render ctx)]
       (set! (.-displayName ~rfs) ~(str cname))
       ~(if (seq wrap-with)
          (reduce (fn [r fi] `(~fi ~r)) rfs wrap-with)
          rfs))))

(defmacro defc
  [& args]
  (let [{:keys [cname docs meta] :as ctx} (parse-defc args)
         wrap-with (or (::wrap meta)
                       (:wrap meta))
        rfs (gensym "component")]
    `(let [~rfs ~(prepare-render ctx)]
       (set! (.-displayName ~rfs) ~(str cname))
       (def ~cname ~docs ~(if (seq wrap-with)
                            (reduce (fn [r fi] `(~fi ~r)) rfs wrap-with)
                            rfs))
       ~(when-let [registry (::register meta)]
          `(swap! ~registry (fn [state#] (assoc state# ~(::register-as meta (keyword (str cname))) ~cname)))))))
