;; This Source Code Form is subject to the terms of the Eclipse Public
;; License - v 1.0
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>
;;
;; Some parts of this code is derived from https://github.com/tonsky/rum

(ns rumext.alpha
  (:refer-clojure :exclude [ref deref])
  (:require-macros rumext.core)
  (:require
   [cljsjs.react]
   [cljsjs.react.dom]
   [goog.object :as gobj]
   [rumext.util :as util :refer [collect collect* call-all]]))

;; --- Impl

(extend-type cljs.core.UUID
  INamed
  (-name [this] (str this))
  (-namespace [_] ""))

(def create-element
  "An alias to the js/React.createElement (only for internal use)."
  js/React.createElement)

(defn get-local-state
  "Given React component, returns Rum state associated with it."
  [comp]
  (-> (unchecked-get comp "state")
      (unchecked-get ":rumext.alpha/state")))

(defn extend!
  [obj props]
  (run! (fn [[k v]] (unchecked-set obj (name k) (clj->js v))) props))

(defn build-class
  [render mixins display-name]
  (let [init           (collect   :init mixins)             ;; state props -> state
        render         render                               ;; state -> [dom state]
        wrap-render    (collect   :wrap-render mixins)      ;; render-fn -> render-fn
        wrapped-render (reduce #(%2 %1) render wrap-render)
        derive-state   (collect   :derive-state mixins)     ;; state -> state
        did-mount      (collect* [:did-mount                ;; state -> state
                                  :after-render] mixins)    ;; state -> state
        should-update  (collect   :should-update mixins)    ;; old-state state -> boolean
        did-update     (collect* [:did-update               ;; state snapshot -> state
                                  :after-render] mixins)    ;; state -> state
        make-snapshot  (collect :make-snapshot mixins)      ;; state -> snapshot
        did-catch      (collect   :did-catch mixins)        ;; state error info -> state
        will-unmount   (collect   :will-unmount mixins)     ;; state -> state
        class-props    (reduce merge (collect :class-properties mixins))  ;; custom prototype properties and methods
        static-props   (reduce merge (collect :static-properties mixins)) ;; custom static properties and methods

        ctor           (fn [props]
                         (this-as this
                           (let [lprops (util/wrap-props props)
                                 lstate (-> {::props lprops ::react-component this}
                                            (call-all init lprops))]
                             (unchecked-set this "state" #js {":rumext.alpha/state" (volatile! lstate)})
                             (.call js/React.Component this props))))
        _              (goog/inherits ctor js/React.Component)
        prototype      (unchecked-get ctor "prototype")]

    (extend! prototype class-props)
    (extend! ctor static-props)

    (unchecked-set ctor "displayName" display-name)

    (unchecked-set ctor "getDerivedStateFromProps"
                   (fn [props state]
                     (let [lstate  @(unchecked-get state ":rumext.alpha/state")
                           nprops  (util/wrap-props props)
                           nstate  (assoc lstate ::props nprops)
                           nstate  (reduce #(%2 %1) nstate derive-state)]
                       ;; allocate new volatile
                       ;; so that we can access both old and new states in shouldComponentUpdate
                       #js {":rumext.alpha/state" (volatile! nstate)})))

    (unchecked-set prototype "render"
                   (fn []
                     (this-as this
                       (let [lstate (get-local-state this)
                             [dom nstate] (wrapped-render @lstate)]
                         (vreset! lstate nstate)
                         dom))))

    (unchecked-set prototype "componentWillUnmount"
                   (fn []
                     (this-as this
                       (let [lstate (get-local-state this)]
                         (when-not (empty? will-unmount)
                           (vswap! lstate call-all will-unmount))
                         (unchecked-set this ":rumext.alpha/unmounted?" true)))))

    (when-not (empty? did-mount)
      (unchecked-set prototype "componentDidMount"
                     (fn []
                       (this-as this
                         (let [lstate (get-local-state this)]
                           (vswap! lstate call-all did-mount))))))

    (when-not (empty? should-update)
      (unchecked-set prototype "shouldComponentUpdate"
                     (fn [new-props new-state]
                       (this-as this
                         (let [old-props (unchecked-get this "props")]
                           (or (some #(% old-props new-props) should-update) false))))))

    (when-not (empty? make-snapshot)
      (unchecked-set prototype "getSnapshotBeforeUpdate"
                     (fn [prev-props prev-state]
                       (let [lstate  @(unchecked-get prev-state ":rumext.alpha/state")]
                         (call-all lstate make-snapshot)))))

    (when-not (empty? did-update)
      (unchecked-set prototype "componentDidUpdate"
                     (fn [_ _ snapshot]
                       (this-as this
                         (let [lstate (get-local-state this)]
                           (vswap! lstate call-all did-update snapshot))))))

    (when-not (empty? did-catch)
      (unchecked-set prototype "componentDidCatch"
                     (fn [error info]
                       (this-as this
                         (let [lstate (get-local-state this)]
                           (vswap! lstate call-all did-catch error
                                   {::component-stack (gobj/get info "componentStack")})
                           (.forceUpdate this))))))
    ctor))

(defn build-lazy
  [builder render mixins display-name]
  (let [klass (delay (builder render mixins display-name))]
    ;; The IFn protocol impl is only for backward compatibility (and
    ;; on benchmarks seems like it does not imples overhead).
    (specify! klass
      cljs.core/IFn
      (-invoke
        ([this props]
         (let [props (cond
                       (map? props) (util/map->obj props)
                       (nil? props) #js {}
                       (object? props) props
                       :else (throw (ex-info "Unexpected props" {:props props})))]
           (create-element @klass props)))))))

(defn build-fnc
  [render display-name metatada]
  (let [factory #(render (util/wrap-props %))]
    (unchecked-set factory "displayName" display-name)
    (if-let [wrap (seq (:wrap metatada []))]
      (reduce #(%2 %1) factory (reverse wrap))
      factory)))

(defn build-def
  [render-body mixins display-name]
  (let [render (fn [state] [(render-body state (::props state)) state])]
    (build-class render mixins display-name)))

;; render queue

(def ^:private schedule
  "Use raf if exsits, if not, schedule is synchronous."
  (let [raf (unchecked-get js/window "requestAnimationFrame")]
    (if (fn? raf) raf (fn [f] (f)))))

(def ^:private batch
  (let [ubu (unchecked-get js/ReactDOM "unstable_batchedUpdates")]
    (if (fn? ubu) ubu (fn [f a] (f a)))))

(def ^:private empty-queue [])
(def ^:private render-queue (volatile! empty-queue))

(defn- render-all [queue]
  (let [not-unmounted? #(not (gobj/get % ":rumext.alpha/unmounted?"))]
    (run! #(.forceUpdate %) (filter not-unmounted? queue))))

(defn- render []
  (let [queue @render-queue]
    (vreset! render-queue empty-queue)
    (batch render-all queue)))

;; --- Main Api

(defn request-render
  "Schedules react component to be rendered on next animation frame."
  [component]
  (when (empty? @render-queue)
    (schedule render))
  (vswap! render-queue conj component))

(defn force-render
  "Schedules react component to be rendered on next animation frame."
  [component]
  (.forceUpdate component))

(defn mount
  "Add element to the DOM tree. Idempotent. Subsequent mounts will
  just update element."
  [element node]
  (js/ReactDOM.render element node)
  nil)

(defn unmount
  "Removes component from the DOM tree."
  [node]
  (js/ReactDOM.unmountComponentAtNode node))

(defn hydrate
  "Same as [[mount]] but must be called on DOM tree already rendered
  by a server via [[render-html]]."
  [element node]
  (js/ReactDOM.hydrate element node))

(defn portal
  "Render `element` in a DOM `node` that is ouside of current DOM hierarchy."
  [element node]
  (js/ReactDOM.createPortal element node))

(defn with-key
  [element key]
  (js/React.cloneElement element #js { "key" key } nil))

(defn with-ref
  [element ref]
  (js/React.cloneElement element #js { "ref" ref } nil))

(defn dom-node
  "Given state, returns top-level DOM node of component. Call it
  during lifecycle callbacks. Can’t be called during render."
  [state]
  (js/ReactDOM.findDOMNode (::react-component state)))

(defn react-component
  "Given state, returns react component associated with."
  [state]
  (::react-component state))

(defn create-ref
  []
  (js/React.createRef))

(defn ref-val
  "Given state and ref handle, returns React component."
  [ref]
  (unchecked-get ref "current"))

(defn set-ref-val!
  [ref val]
  (unchecked-set ref "current" val))

(defn ref-node
  "Given state and ref handle, returns DOM node associated with ref."
  [ref]
  (js/ReactDOM.findDOMNode (ref-val ref)))

;; --- Mixins

(def memo
  "Mixin. Will avoid re-render if none of component’s arguments have
  changed. Does equality check (`identical?`) on all arguments."
  {:should-update (fn [old-props new-props]
                    (not (util/props-equals? identical? old-props new-props)))})

(def pure
  "Mixin. Will avoid re-render if none of component’s arguments have
  changed. Does equalty check (`=`) on all
  arguments."
  {:should-update (fn [old-props new-props]
                    (not (util/props-equals? = old-props new-props)))})

(def static
  "Mixin. Will avoid re-render."
  {:should-update (constantly false)})

;; local mixin

(def sync-render
  "A special mixin for mark posible renders of the component to use
  synchronous rendering (async by default). Mainly needed for forms
  related pages."
  {:init (fn [own props] (assoc own ::sync-render true))})

(defn local
  ([] (local {} ::local))
  ([initial] (local initial ::local))
  ([initial key]
   {:init
    (fn [state props]
      (let [lstate (atom initial)
            component (::react-component state)]
        (if (::sync-render state)
          (add-watch lstate key #(force-render component))
          (add-watch lstate key #(request-render component)))
        (assoc state key lstate)))}))

(def ^:dynamic *reactions*)

(def reactive
  {:init
   (fn [state props]
     (assoc state ::reactions-key (gensym "reactive")))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (binding [*reactions* (volatile! #{})]
         (let [comp             (::react-component state)
               old-reactions    (::reactions state #{})
               [dom next-state] (render-fn state)
               new-reactions    (cljs.core/deref *reactions*)
               key              (::reactions-key state)
               sync-render?     (::sync-render state)]

           (run! (fn [ref]
                   (when-not (contains? new-reactions ref)
                     (remove-watch ref key))) old-reactions)
           (run! (fn [ref]
                   (when-not (contains? old-reactions ref)
                     (add-watch ref key
                                (fn [_ _ _ _]
                                  (if sync-render?
                                    (force-render comp)
                                    (request-render comp)))))) new-reactions)
           [dom (assoc next-state ::reactions new-reactions)]))))
   :will-unmount
   (fn [{:keys [::reactions-key ::reactions] :as state}]
     (run! (fn [ref] (remove-watch ref reactions-key)) reactions)
     (dissoc state ::reactions ::reactions-key))
   })

(defn react
  "Works in conjunction with [[reactive]] mixin. Use this function
  instead of `deref` inside render, and your component will subscribe
  to changes happening to the derefed atom."
  [ref]
  (assert *reactions* "rumext.alpha/react is only supported in conjunction with rumext.alpha/reactive")
  (vswap! *reactions* conj ref)
  (cljs.core/deref ref))

;; --- Raw Hooks

(defn useRef
  [initial]
  (js/React.useRef initial))

(defn useState
  [initial]
  (js/React.useState initial))

(defn useEffect
  [f deps]
  (js/React.useEffect f deps))

(defn useMemo
  [f deps]
  (js/React.useMemo f deps))

(defn useCallback
  [f deps]
  (js/React.useCallback f deps))

(defn useLayoutEffect
  [f deps]
  (js/React.useLayoutEffect f deps))

;; --- Hooks

;; The cljs version of use-ref is identical to the raw (no
;; customizations/adaptations needed)
(def use-ref useRef)

(defn use-state
  [initial]
  (let [[state set-state] (useState initial)]
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

(defn use-var
  "A custom hook for define mutable variables that persists
  on renders (based on useRef hook)."
  [initial]
  (let [ref (useRef initial)]
    (reify
      cljs.core/IReset
      (-reset! [_ new-value]
        (set-ref-val! ref new-value))

      cljs.core/ISwap
      (-swap! [self f]
        (set-ref-val! ref (f (ref-val ref))))
      (-swap! [self f x]
        (set-ref-val! ref (f (ref-val ref) x)))
      (-swap! [self f x y]
        (set-ref-val! ref (f (ref-val ref) x y)))
      (-swap! [self f x y more]
        (set-ref-val! ref (apply f (ref-val ref) x y more)))

      cljs.core/IDeref
      (-deref [self] (ref-val ref)))))

(defn- use-effect-impl
  [f deps]
  (useEffect
   #(let [r (f)] (if (fn? r) r (constantly nil)))
   (cond
     (nil? deps) #js []
     (array? deps) deps
     (true? deps) nil
     (vector? deps) (into-array deps)
     :else #js [deps])))

(defn use-effect
  ([fn-or-opts]
   (cond
     (fn? fn-or-opts)
     (use-effect-impl fn-or-opts nil)

     (map? fn-or-opts)
     (use-effect-impl (:fn fn-or-opts)
                      (:deps fn-or-opts))
     :else
     (throw (ex-info "Invalid arguments" {}))))
  ([f deps]
   (use-effect-impl f deps)))

(defn- use-layout-effect-impl
  [f deps]
  (useLayoutEffect
   #(let [r (f)] (if (fn? r) r (constantly nil)))
   (cond
     (nil? deps) #js []
     (array? deps) deps
     (true? deps) nil
     (vector? deps) (into-array deps)
     :else #js [deps])))

(defn use-layout-effect
  ([fn-or-opts]
   (cond
     (fn? fn-or-opts)
     (use-layout-effect-impl fn-or-opts nil)

     (map? fn-or-opts)
     (use-layout-effect-impl (:fn fn-or-opts)
                             (:deps fn-or-opts))
     :else
     (throw (ex-info "Invalid arguments" {}))))
  ([f deps]
   (use-layout-effect-impl f deps)))

(defn- use-memo-impl
  [f deps]
  (useMemo
   (fn [] (f))
   (cond
     (nil? deps) #js []
     (array? deps) deps
     (true? deps) nil
     (vector? deps) (into-array deps)
     :else #js [deps])))

(defn use-memo
  ([fn-or-opts]
   (cond
     (fn? fn-or-opts)
     (use-memo-impl fn-or-opts nil)

     (map? fn-or-opts)
     (use-memo-impl (:fn fn-or-opts)
                    (:deps fn-or-opts))
     :else
     (throw (ex-info "Invalid arguments" {}))))
  ([f deps]
   (use-memo-impl f deps)))

(defn- use-callback-impl
  [f deps]
  (useCallback
   (if (fn? f) f (fn [] (f)))
   (cond
     (nil? deps) #js []
     (array? deps) deps
     (true? deps) nil
     (vector? deps) (into-array deps)
     :else #js [deps])))

(defn use-callback
  ([fn-or-opts]
   (cond
     (fn? fn-or-opts)
     (use-callback-impl fn-or-opts nil)

     (map? fn-or-opts)
     (use-callback-impl (:fn fn-or-opts)
                        (:deps fn-or-opts))
     :else
     (throw (ex-info "Invalid arguments" {}))))
  ([f deps]
   (use-callback-impl f deps)))

(defn deref
  [iref]
  (let [[state set-state!] (useState #(cljs.core/deref iref))]
    (useEffect
     (fn []
       (let [key (gensym "use-deref")]
         (add-watch iref key #(set-state! %4))
         (fn [] (remove-watch iref key))))
     #js [iref])
    state))

;; --- Higher-Order Components

(defn wrap-memo
  ([component]
   (js/React.memo component))
  ([component eq?]
   (js/React.memo component #(util/props-equals? eq? %1 %2))))

;; --- Other API

(defn element
  ([klass]
   (let [klass (if (delay? klass) @klass klass)]
     (create-element klass #js {})))
  ([klass props]
   (let [klass (if (delay? klass) @klass klass)
         props (cond
                 (map? props) (util/map->obj props)
                 (object? props) props
                 :else (throw (ex-info "Unexpected props" {:props props})))]
     (create-element klass props))))
