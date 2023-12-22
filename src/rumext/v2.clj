;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) Andrey Antukh <niwi@niwi.nz>

(ns rumext.v2
  (:require
   [cljs.core :as-alias c]
   [rumext.v2.compiler :as hc]))

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
          (recur (assoc r :metadata v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      3 (if (vector? v)
          (recur (assoc r :args v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid macro definition: expected component args vector" {})))
      4 {:cname (:cname r)
         :docs  (str (:doc r))
         :arg-1  (first (:args r))
         :arg-n  (rest (:args r))
         :body  (cons v n)
         :meta  (:metadata r)})))

(defn- prepare-render
  [{:keys [cname meta arg-1 arg-n body] :as ctx}]
  (let [props-sym (with-meta (gensym "props-") {:tag 'js})
        args      (cons props-sym arg-n)
        simple?   (fn [s]
                    (some? (re-matches #"[A-Za-z0-9_]+" s)))

        f         `(fn ~cname [~@(if arg-1 args [])]
                     (let [~@(cond
                               (and (some? arg-1) (::wrap-props meta true))
                               [arg-1 `(rumext.v2.util/wrap-props ~props-sym)]

                               (symbol? arg-1)
                               [arg-1 props-sym]

                               (and (map? arg-1) (not (::wrap-props meta true)))
                               (let [alias (get arg-1 :as)
                                     alts  (get arg-1 :or)
                                     items (some-> (get arg-1 :keys) set)]
                                 (cond->> []
                                   (symbol? alias)
                                   (into [alias props-sym])

                                   (set? items)
                                   (concat
                                    (mapcat (fn [k]
                                              (let [prop-name (name k)
                                                    accessor  (if (simple? prop-name)
                                                                (list '. props-sym (symbol (str "-" prop-name)))
                                                                (list 'cljs.core/unchecked-get props-sym prop-name))]
                                                [(if (symbol? k) k (symbol prop-name))
                                                 (if (contains? alts k)
                                                   `(~'js* "~{} ?? ~{}" ~accessor ~(get alts k))
                                                   accessor)]))
                                            items)))))]

                       ~@(butlast body)
                       (html ~(last body)))
                     )]

    (if (::forward-ref meta)
      `(rumext.v2/forward-ref ~f)
      f)))



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

(defmacro with-memo
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

(defmacro with-fn
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

(defn production-build?
  []
  (let [env (System/getenv)]
    (or (= "production" (get env "NODE_ENV"))
        (= "production" (get env "RUMEXT_ENV"))
        (= "production" (get env "TARGET_ENV")))))

(defmacro lazy-component
  [ns-sym]
  (if (production-build?)
    `(let [loadable# (shadow.lazy/loadable ~ns-sym)]
       (rumext.v2/lazy (fn []
                         (.then (shadow.lazy/load loadable#)
                                (fn [component]
                                  (js-obj "default" component))))))
    `(let [loadable# (shadow.lazy/loadable ~ns-sym)]
       (rumext.v2/lazy (fn []
                         (.then (shadow.lazy/load loadable#)
                                (fn [_#]
                                  (js-obj "default"
                                          (rumext.v2/fnc ~'wrapper
                                            {:rumext.v2/wrap-props false}
                                            [props#]
                                            [:> (deref loadable#) props#])))))))))
