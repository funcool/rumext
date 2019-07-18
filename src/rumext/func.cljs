;; This Source Code Form is subject to the terms of the Eclipse Public
;; License - v 1.0
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>

(ns rumext.func
  (:refer-clojure :exclude [ref])
  (:require-macros rumext.func)
  (:require
   [cljsjs.react]
   [cljsjs.react.dom]
   [rumext.util :as util]))

;; --- Macros Impl

(defn build-fn-ctor
  [render display-name metatada]
  (let [factory (fn [props]
                  (let [props (util/wrap-props props)]
                    (render props)))]
    (unchecked-set factory "displayName" display-name)
    (if-let [wrap (seq (:wrap metatada []))]
      (reduce #(%2 %1) factory wrap)
      factory)))

;; --- Hooks

(defn use-state
  [initial]
  (let [[state set-state] (js/React.useState initial)]
    (reify
      cljs.core/IReset
      (-reset! [_ new-value]
        (set-state new-value))

      cljs.core/ISwap
      (-swap! [self f]
        (set-state f))
      (-swap! [self f x]
        (set-state #(f % x)))
      (-swap! [self f x y]
        (set-state #(f % x y)))
      (-swap! [self f x y more]
        (set-state #(apply f % x y more)))

     cljs.core/IDeref
     (-deref [self] state))))

(defn use-ref
  [initial]
  (let [ref (js/React.useRef initial)]
    (reify
      cljs.core/IReset
      (-reset! [_ new-value]
        (set! (.-current ref) new-value))

      cljs.core/ISwap
      (-swap! [self f]
        (set! (.-current ref) (f (.-current ref))))
      (-swap! [self f x]
        (set! (.-current ref) (f (.-current ref) x)))
      (-swap! [self f x y]
        (set! (.-current ref) (f (.-current ref) x y)))
      (-swap! [self f x y more]
        (set! (.-current ref) (apply f (.-current ref) x y more)))

      cljs.core/IDeref
      (-deref [self] (.-current ref)))))

(defn use-effect
  [& {:keys [init end watch] :or {init identity}}]
  (let [watch (cond
                (array? watch) watch
                (true? watch) nil
                (nil? watch) #js []
                (vector? watch) (into-array watch))]
    (js/React.useEffect
     #(let [r (init)]
        (when (fn? end)
          (fn [] (end r))))
     watch)))

(defn use-memo
  ([callback] (use-memo #js [] callback))
  ([watch callback]
   (let [watch (cond
                 (array? watch) watch
                 (true? watch) nil
                 (nil? watch) #js []
                 (vector? watch) (into-array watch))]
    (js/React.useMemo callback watch))))

;; --- High Order Components

(def ^:private ^:dynamic *reactions*)

(defn react
  "Works in conjunction with [[reactive]] mixin. Use this function
  instead of `deref` inside render, and your component will subscribe
  to changes happening to the derefed atom."
  [ref]
  (assert *reactions* "rumext.core/react is only supported in conjunction with rumext.core/reactive")
  (vswap! *reactions* conj ref)
  @ref)

(defn- wrap-reactive
  [component]
  (rumext.func/fnc wrapper [props]
    (binding [*reactions* (volatile! #{})]
      (let [key-ref (use-ref (random-uuid))
            reactions-ref (use-ref #{})
            state (use-state (int 0))
            dom (component props)
            new-reactions (deref *reactions*)
            trigger-render #(swap! state unchecked-inc-int)
            old-reactions (deref reactions-ref)
            key (deref key-ref)]

        (use-effect
         :end #(doseq [ref @reactions-ref]
                 (remove-watch ref key)))

        (doseq [ref old-reactions]
          (when-not (contains? new-reactions ref)
            (remove-watch ref key)))

        (doseq [ref new-reactions]
          (when-not (contains? old-reactions ref)
            (add-watch ref key trigger-render)))

        (reset! reactions-ref new-reactions)
        dom))))

(defn reactive
  [component]
  (let [dname (.-displayName component)
        result (wrap-reactive component)]
    (unchecked-set result "displayName" dname)
    result))

(defn memo
  [component]
  (js/React.memo component
                 (fn [prev next]
                   (= (util/wrap-props prev)
                      (util/wrap-props next)))))

(defn element
  ([klass]
   (js/React.createElement klass #js {}))
  ([klass props]
   (->> (if (map? props) (util/map->obj props) props)
        (js/React.createElement klass))))
