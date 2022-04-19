;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) Andrey Antukh <niwi@niwi.nz>

(ns rumext.alpha
  (:require
   [rumext.compiler :as hc]))

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

(defmacro with-memo
  [deps & body]
  (cond
    (vector? deps)
    `(rumext.alpha/use-memo
      (rumext.alpha/deps ~@deps)
      (fn [] ~@body))


    (nil? deps)
    `(rumext.alpha/use-memo
      nil
      (fn [] ~@body))

    :else
    `(rumext.alpha/use-memo
      (fn [] ~@(cons deps body)))))

(defmacro with-effect
  [deps & body]
  (cond
    (vector? deps)
    `(rumext.alpha/use-effect
      (rumext.alpha/deps ~@deps)
      (fn [] ~@body))

    (nil? deps)
    `(rumext.alpha/use-effect
      nil
      (fn [] ~@body))

    :else
    `(rumext.alpha/use-effect
      (fn [] ~@(cons deps body)))))
