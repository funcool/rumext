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
   [rumext.v2.compiler :as hc]
   [rumext.v2.util :as util]))

(create-ns 'rumext.v2.util)

(defn ^:no-doc production-build?
  []
  (let [env (System/getenv)]
    (or (= "production" (get env "NODE_ENV"))
        (= "production" (get env "RUMEXT_ENV"))
        (= "production" (get env "TARGET_ENV")))))

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
  (let [default-style (if (str/ends-with? (name cname) "*") :obj :clj)]
    (cond
      (contains? meta ::props)
      (= :clj (get meta ::props default-style))

      (contains? meta ::wrap-props)
      (get meta ::wrap-props)

      (str/ends-with? (name cname) "*")
      false

      :else
      true)))

(defn- react-props?
  [{:keys [meta cname] :as ctx}]
  (and (not (wrap-props? ctx))
       (or (str/ends-with? (name cname) "*")
           (= (::props meta) :react))))

(defn- simple-ident?
  [s]
  (some? (re-matches #"[A-Za-z0-9_]+" s)))

(defn- prepare-let-bindings
  [{:keys [cname meta props body params] :as ctx}]
  (let [react-props? (react-props? ctx)
        psym         (first params)]
    (cond
      (and (some? props) (wrap-props? ctx))
      [props (list 'rumext.v2.util/wrap-props psym)]

      (and (map? props) (not (wrap-props? ctx)))
      (let [alias (get props :as)
            alts  (get props :or)
            other (or (get props :&)
                      (get props :rest))
            items (some-> (get props :keys) set)]
        (cond->> []
          (symbol? alias)
          (into [alias psym])

          (symbol? other)
          (into [other (list 'js* "undefined")])

          (set? items)
          (concat (mapcat (fn [k]
                            (let [prop-name (if react-props?
                                              (util/ident->prop k)
                                              (name k))
                                  accessor  (if (simple-ident? prop-name)
                                              (list '. psym (symbol (str "-" prop-name)))
                                              (list 'cljs.core/unchecked-get psym prop-name))]

                              [(if (symbol? k) k (symbol prop-name))
                               (cond
                                 ;; If the other symbol is present, then a
                                 ;; different destructuring stragegy will be
                                 ;; used so we need to set here the value to
                                 ;; 'undefined'
                                 (symbol? other)
                                 (list 'js* "undefined")

                                 (contains? alts k)
                                 `(~'js* "~{} ?? ~{}" ~accessor ~(get alts k))

                                 :else
                                 accessor)]))
                          items))))

      (symbol? props)
      [props psym])))

(defn native-destructure
  "Generates a js var line with native destructuring. Only used when :&
  used in destructuring."
  [{:keys [props params props] :as ctx}]

  ;; Emit native destructuring only if the :& key has value
  (when (or (symbol? (:& props))
            (symbol? (:rest props)))

    (let [react-props? (react-props? ctx)
          psym         (first params)

          keys-props (:keys props [])
          all-alias  (:as props)
          rst-alias  (or (:& props) (:rest props))

          k-props    (dissoc props :keys :as :& :rest)
          k-props    (->> (:keys props [])
                          (remove (comp simple-ident? name))
                          (map (fn [k] [k k]))
                          (into k-props))

          props      (->> (:keys props [])
                          (filter (comp simple-ident? name))
                          (mapv (fn [k]
                                  (if react-props?
                                    (util/ident->prop k)
                                    (name k)))))

          params     []

          [props params]
          (if (seq k-props)
            (reduce (fn [[props params] [ks kp]]
                      ;; (prn "KKK" ks kp)
                      (let [kp (if react-props?
                                 (util/ident->prop kp)
                                 (name kp))]
                        [(conj props (str "~{}: ~{}"))
                         (conj params kp ks)]))
                    [props params]
                    k-props)
            [props params])

          [props params]
          (if (symbol? rst-alias)
            [(conj props "...~{}") (conj params rst-alias)]
            [props params])

          tmpl    (str "var {"
                       (str/join ", " props)
                       "} = ~{}")
          params  (conj params psym)]

      [(apply list 'js* tmpl params)])))

(defn- prepare-props-checks
  [{:keys [cname meta params] :as ctx}]
  (let [react-props? (react-props? ctx)
        psym         (vary-meta (first params) assoc :tag 'js)]
    (when *assert*
      (cond
        (::schema meta)
        (let [validator-sym (with-meta (symbol (str cname "-validator"))
                              {:tag 'function})]
          (concat
           (cons (list 'js* "// ===== start props checking =====") nil)
           [`(let [res# (~validator-sym ~psym)]
               (when (some? res#)
                 (let [res# (first res#)
                       msg# (str ~(str "invalid props on component " (str cname) " ")
                                 "('" (name (key res#)) "' " (val res#) ")\n")]
                   (throw (js/Error. msg#)))))]
           (cons (list 'js* "// ===== end props checking =====") nil)))

        (::expect meta)
        (let [props (::expect meta)]
          (concat
           (cons (list 'js* "// ===== start props checking =====") nil)
           (if (map? props)
             (->> props
                  (map (fn [[prop pred-sym]]
                         (let [prop (if react-props?
                                      (util/ident->prop prop)
                                      (name prop))

                               accs (if (simple-ident? prop)
                                      (list '. psym (symbol (str "-" prop)))
                                      (list 'cljs.core/unchecked-get psym prop))

                               expr `(~pred-sym ~accs)]
                           `(when-not ~(vary-meta expr assoc :tag 'boolean)
                              (throw (js/Error. ~(str "invalid value for '" prop "'"))))))))

             (->> props
                  (map (fn [prop]
                         (let [prop (if react-props?
                                      (util/ident->prop prop)
                                      (name prop))
                               expr `(.hasOwnProperty ~psym ~prop)]
                           `(when-not ~(vary-meta expr assoc :tag 'boolean)
                              (throw (js/Error. ~(str "missing prop '" prop "'")))))))))
           (cons (list 'js* "// ===== end props checking =====") nil)))

        :else
        []))))

(defn- prepare-render-fn
  [{:keys [cname meta body params props] :as ctx}]
  (let [f `(fn ~cname ~params
             ~@(prepare-props-checks ctx)
             (let [~@(prepare-let-bindings ctx)]
               ~@(native-destructure ctx)

               ~@(butlast body)
               ~(hc/compile (last body))))]
    (if (::forward-ref meta)
      `(rumext.v2/forward-ref ~f)
      f)))

(defn- resolve-wrappers
  [{:keys [cname docs meta] :as ctx}]
  (let [wrappers     (or (::wrap meta) (:wrap meta) [])
        react-props? (react-props? ctx)
        memo         (::memo meta)]
    (cond
      (set? memo)
      (let [eq-f (or (::memo-equals ctx) 'cljs.core/=)
            np-s (with-meta (gensym "new-props-") {:tag 'js})
            op-s (with-meta (gensym "old-props-") {:tag 'js})
            op-f (fn [prop]
                   (let [prop (if react-props?
                                (util/ident->prop prop)
                                (name prop))
                         accs (if (simple-ident? prop)
                                (let [prop (symbol (str "-" (name prop)))]
                                  (list eq-f
                                        (list '.. np-s prop)
                                        (list '.. op-s prop)))
                                (list eq-f
                                      (list 'cljs.core/unchecked-get np-s prop)
                                      (list 'cljs.core/unchecked-get op-s prop)))]
                     (with-meta accs {:tag 'boolean})))]
        (conj wrappers
              `(fn [component#]
                 (mf/memo' component# (fn [~np-s ~op-s]
                                        (and ~@(map op-f memo)))))))

      (true? memo)
      (if-let [eq-f (::memo-equals meta)]
        (conj wrappers `(fn [component#]
                          (mf/memo component# ~eq-f)))
        (conj wrappers 'rumext.v2/memo'))

      :else wrappers)))

(defmacro fnc
  "A macro for defining inline component functions. Look the user guide for
  understand how to use it."
  [& args]
  (let [{:keys [cname meta] :as ctx} (parse-defc args)
        wrappers (resolve-wrappers ctx)
        rfs      (gensym (str cname "__"))]
    `(let [~rfs ~(if (seq wrappers)
                   (reduce (fn [r fi] `(~fi ~r)) (prepare-render-fn ctx) wrappers)
                   (prepare-render-fn ctx))]
       ~@(when-not (production-build?)
           [`(set! (.-displayName ~rfs) ~(str cname))])
       ~rfs)))

(defmacro defc
  "A macro for defining component functions. Look the user guide for
  understand how to use it."
  [& args]
  (let [{:keys [cname docs meta] :as ctx} (parse-defc args)
        wrappers     (resolve-wrappers ctx)
        react-props? (react-props? ctx)
        cname        (if (::private meta)
                       (vary-meta cname assoc :private true)
                       cname)]
    `(do
       ~@(when (and (::schema meta) *assert*)
           (let [validator-sym (with-meta (symbol (str cname "-validator"))
                                 {:tag 'function})]
             [`(def ~validator-sym (rumext.v2.util/validator ~(::schema meta) ~react-props?))]))

       (def ~cname ~docs ~(if (seq wrappers)
                            (reduce (fn [r fi] `(~fi ~r)) (prepare-render-fn ctx) wrappers)
                            (prepare-render-fn ctx)))

       ~@(when-not (production-build?)
           [`(set! (.-displayName ~cname) ~(str cname))])

       ~(when-let [registry (::register meta)]
          `(swap! ~registry (fn [state#] (assoc state# ~(::register-as meta (keyword (str cname))) ~cname))))
       )))

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
                                                      {:rumext.v2/props :obj}
                                                      [props#]
                                                      [:> (deref loadable#) props#])))))))))

(defmacro spread
  "A helper for create spread js object operations. Leaves the keys
  untouched."
  [target & [other :as rest]]
  (assert (or (symbol? target)
              (map? target))
          "only symbols or maps accepted on target")
  (assert (or (= (count rest) 0)
              (and (= (count rest) 1)
                   (or (symbol? other)
                       (map? other)))
              (and (even? (count rest))
                   (or (keyword? other)
                       (string? other))))
          "only symbols, map or named parameters allowed for the spread")
  (let [other (cond
                (> (count rest) 1) (apply hash-map rest)
                (= (count rest) 0) {}
                :else              other)]
    (hc/compile-to-js-spread target other identity)))

(defmacro spread-props
  "A helper for create spread js object operations. Adapts compile
  time known keys to the react props standard transformations."
  [target & [other :as rest]]
  (assert (or (symbol? target)
              (map? target))
          "only symbols or maps accepted on target")
  (assert (or (= (count rest) 0)
              (and (= (count rest) 1)
                   (or (symbol? other)
                       (map? other)))
              (and (even? (count rest))
                   (or (keyword? other)
                       (string? other))))
          "only symbols, map or named parameters allowed for the spread")
  (let [other (cond
                (> (count rest) 1) (apply hash-map rest)
                (= (count rest) 0) {}
                :else              other)]
     (hc/compile-to-js-spread target other hc/compile-prop)))
