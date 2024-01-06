;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) Andrey Antukh <niwi@niwi.nz>

(ns rumext.v2
  (:refer-clojure :exclude [simple-ident?])
  (:require
   [cljs.core :as-alias c]
   [clojure.string :as str]
   [rumext.v2.compiler :as hc]))

(create-ns 'rumext.v2.util)

(defmacro html
  [body]
  (hc/compile body))

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
          (recur (assoc r :meta v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      3 (if (vector? v)
          (recur (assoc r :args v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid macro definition: expected component args vector" {})))

      (let [psym (with-meta (gensym "props-") {:tag 'js})]
        {:cname  (:cname r)
         :docs   (str (:doc r))
         :props  (first (:args r))
         :params (into [psym] (rest (:args r)))
         :body   (cons v n)
         :psym   psym
         :meta   (:meta r)}))))

(defn- wrap-props?
  [{:keys [cname meta]}]
  (let [default-style (if (str/ends-with? (name cname) "*") :native :clj)]
    (cond
      (contains? meta ::props)
      (= :clj (get meta ::props default-style))

      (contains? meta ::wrap-props)
      (get meta ::wrap-props)

      (str/ends-with? (name cname) "*")
      false

      :else
      true)))

(defn- lisp-to-native-props?
  [{:keys [meta cname] :as ctx}]
  (and (not (wrap-props? ctx))
       (or (str/ends-with? (name cname) "*")
           (= (::props-destructuring meta) :lisp-to-camel))))

(defn- simple-ident?
  [s]
  (some? (re-matches #"[A-Za-z0-9_]+" s)))

(defn- prepare-let-bindings
  [{:keys [cname meta props body params] :as ctx}]
  (let [l2n? (lisp-to-native-props? ctx)
        psym (first params)]
    (cond
      (and (some? props) (wrap-props? ctx))
      [props (list 'rumext.v2.util/wrap-props psym)]

      (and (map? props) (not (wrap-props? ctx)))
      (let [alias (get props :as)
            alts  (get props :or)
            items (some-> (get props :keys) set)]
        (cond->> []
          (symbol? alias)
          (into [alias psym])

          (set? items)
          (concat (mapcat (fn [k]
                            (let [prop-name (if l2n?
                                              (name (hc/camel-case k))
                                              (name k))
                                  accessor  (if (simple-ident? prop-name)
                                              (list '. psym (symbol (str "-" prop-name)))
                                              (list 'cljs.core/unchecked-get psym prop-name))]
                              [(if (symbol? k) k (symbol prop-name))
                               (if (contains? alts k)
                                 `(~'js* "~{} ?? ~{}" ~accessor ~(get alts k))
                                 accessor)]))
                          items))))

      (symbol? props)
      [props psym])))

(defn- prepare-render-fn
  [{:keys [cname meta body params] :as ctx}]
  (let [f `(fn ~cname ~params
             (let [~@(prepare-let-bindings ctx)]
               ~@(butlast body)
               ~(hc/compile (last body))))]
    (if (::forward-ref meta)
      `(rumext.v2/forward-ref ~f)
      f)))

(defmacro fnc
  "A macro for defining inline component functions. Look the user guide for
  understand how to use it."
  [& args]
  (let [{:keys [cname meta] :as ctx} (parse-defc args)
        wrap-with (or (::wrap meta)
                      (:wrap meta))
        rfs (gensym "component-")]
    `(let [~rfs ~(prepare-render-fn ctx)]
       (set! (.-displayName ~rfs) ~(str cname))
       ~(if (seq wrap-with)
          (reduce (fn [r fi] `(~fi ~r)) rfs wrap-with)
          rfs))))

(defmacro defc
  "A macro for defining component functions. Look the user guide for
  understand how to use it."
  [& args]
  (let [{:keys [cname docs meta] :as ctx} (parse-defc args)
        wrap-with (or (::wrap meta)
                      (:wrap meta))
        rfs (gensym "component-")]
    `(let [~rfs ~(prepare-render-fn ctx)]
       (set! (.-displayName ~rfs) ~(str cname))
       (def ~cname ~docs ~(if (seq wrap-with)
                            (reduce (fn [r fi] `(~fi ~r)) rfs wrap-with)
                            rfs))
       ~(when-let [registry (::register meta)]
          `(swap! ~registry (fn [state#] (assoc state# ~(::register-as meta (keyword (str cname))) ~cname)))))))

(defmacro with-memo
  "A convenience syntactic abstraction (macro) for `useMemo`"
  [deps & body]
  (cond
    (vector? deps)
    `(rumext.v2/use-memo
      (rumext.v2/deps ~@deps)
      (fn [] ~@body))


    (nil? deps)
    `(rumext.v2/use-memo
      nil
      (fn [] ~@body))

    :else
    `(rumext.v2/use-memo
      (fn [] ~@(cons deps body)))))

(defmacro ^:no-doc with-fn
  [deps & body]
  (cond
    (vector? deps)
    `(rumext.v2/use-fn
      (rumext.v2/deps ~@deps)
      ~@body)


    (nil? deps)
    `(rumext.v2/use-fn
      nil
      ~@body)

    :else
    `(rumext.v2/use-fn
      ~@(cons deps body))))

(defmacro with-effect
  "A convenience syntactic abstraction (macro) for `useEffect`"
  [deps & body]
  (cond
    (vector? deps)
    `(rumext.v2/use-effect
      (rumext.v2/deps ~@deps)
      (fn [] ~@body))

    (nil? deps)
    `(rumext.v2/use-effect
      nil
      (fn [] ~@body))

    :else
    `(rumext.v2/use-effect
      (fn [] ~@(cons deps body)))))

(defmacro with-layout-effect
  "A convenience syntactic abstraction (macro) for `useLayoutEffect`"
  [deps & body]
  (cond
    (vector? deps)
    `(rumext.v2/use-layout-effect
      (rumext.v2/deps ~@deps)
      (fn [] ~@body))

    (nil? deps)
    `(rumext.v2/use-layout-effect
      nil
      (fn [] ~@body))

    :else
    `(rumext.v2/use-layout-effect
      (fn [] ~@(cons deps body)))))

(defmacro check-props
  "A macro version of the `check-props` function"
  [props & [eq-f :as rest]]
  (if (symbol? props)
    `(apply rumext.v2/check-props ~props ~rest)

    (let [eq-f (or eq-f 'cljs.core/=)
          np-s (with-meta (gensym "new-props-") {:tag 'js})
          op-s (with-meta (gensym "old-props-") {:tag 'js})
          op-f (fn [prop]
                 (let [prop-access (symbol (str "-" (name prop)))]
                   (with-meta
                     (if (simple-ident? prop)
                       (list eq-f
                             (list '.. np-s prop-access)
                             (list '.. op-s prop-access))
                       (list eq-f
                             (list 'cljs.core/unchecked-get np-s prop)
                             (list 'cljs.core/unchecked-get op-s prop)))
                     {:tag 'boolean})))]
      `(fn [~np-s ~op-s]
         (and ~@(map op-f props))))))

(defn ^:no-doc production-build?
  []
  (let [env (System/getenv)]
    (or (= "production" (get env "NODE_ENV"))
        (= "production" (get env "RUMEXT_ENV"))
        (= "production" (get env "TARGET_ENV")))))

(defmacro lazy-component
  "A macro that helps defining lazy-loading components with the help
  of shadow-cljs tooling."
  [ns-sym]
  (if (production-build?)
    `(let [loadable# (shadow.lazy/loadable ~ns-sym)]
       (rumext.v2/lazy (fn []
                         (.then (shadow.lazy/load loadable#)
                                (fn [component#]
                                  (cljs.core/js-obj "default" component#))))))
    `(let [loadable# (shadow.lazy/loadable ~ns-sym)]
       (rumext.v2/lazy (fn []
                         (.then (shadow.lazy/load loadable#)
                                (fn [_#]
                                  (cljs.core/js-obj "default"
                                                    (rumext.v2/fnc ~'wrapper
                                                      {:rumext.v2/wrap-props false}
                                                      [props#]
                                                      [:> (deref loadable#) props#])))))))))
(defmacro spread-obj
  "A helper for create spread js object operations."
  [target & [other :as rest]]
  (assert (or (symbol? target)
              (map? target))
          "only symbols or maps accepted on target")
  (assert (or (and (= (count rest) 1)
                   (or (symbol? other)
                       (map? other)))
              (and (even? (count rest))
                   (or (keyword? other)
                       (string? other))))
          "only symbols, map or named parameters allowed for the spread")
  (let [other (if (> (count rest) 1)
                (apply hash-map rest)
                other)]
    (hc/compile-to-spread-js-obj target other)))
