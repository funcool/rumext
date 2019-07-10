;; This Source Code Form is subject to the terms of the Eclipse Public
;; License - v 1.0
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>
;;
;; Some code is copied and/or derived from https://github.com/tonsky/rum

(ns rumext.core
  (:refer-clojure :exclude [ref])
  (:require-macros rumext.core)
  (:require
   [cljsjs.react]
   [cljsjs.react.dom]
   [goog.object :as gobj]
   [rumext.util :as util :refer [collect collect* call-all]]))

(extend-type cljs.core.UUID
  INamed
  (-name [this] (str this))
  (-namespace [_] ""))

(defn state
  "Given React component, returns Rum state associated with it."
  [comp]
  (gobj/get (.-state comp) ":rum/state"))

(defn- extend! [obj props]
  (doseq [[k v] props
          :when (some? v)]
    (gobj/set obj (name k) (clj->js v))))

(defn- build-class
  [render mixins display-name]
  (let [init           (collect   :init mixins)             ;; state props -> state
        render         render                               ;; state -> [dom state]
        wrap-render    (collect   :wrap-render mixins)      ;; render-fn -> render-fn
        wrapped-render (reduce #(%2 %1) render wrap-render)
        derive-state   (collect   :derive-state mixins)     ;; state -> state
        did-mount      (collect* [:did-mount                ;; state -> state
                                  :after-render] mixins)    ;; state -> state
        should-update  (collect   :should-update mixins)    ;; old-state state -> boolean
        did-update     (collect* [:did-update               ;; state -> state
                                  :after-render] mixins)    ;; state -> state
        make-snapshot  (collect :make-snapshot mixins)      ;; state -> state
        did-catch      (collect   :did-catch mixins)        ;; state error info -> state
        will-unmount   (collect   :will-unmount mixins)     ;; state -> state
        class-props    (reduce merge (collect :class-properties mixins))  ;; custom prototype properties and methods
        static-props   (reduce merge (collect :static-properties mixins)) ;; custom static properties and methods

        ctor           (fn [props]
                         (this-as this
                           (gobj/set this "state"
                             #js {":rum/state"
                                  (-> (gobj/get props ":rum/initial-state")
                                      (assoc :rum/react-component this)
                                      (call-all init props)
                                      volatile!)})
                           (.call js/React.Component this props)))
        _              (goog/inherits ctor js/React.Component)
        prototype      (gobj/get ctor "prototype")]

    (gobj/set ctor "getDerivedStateFromProps"
              (fn [next-props old-state]
                (let [old-state  @(gobj/get old-state ":rum/state")
                      state      (merge old-state (gobj/get next-props ":rum/initial-state"))
                      next-state (reduce #(%2 %1) state derive-state)]
                  ;; allocate new volatile
                  ;; so that we can access both old and new states in shouldComponentUpdate
                  #js {":rum/state" (volatile! next-state)})))

    (when-not (empty? did-mount)
      (gobj/set prototype "componentDidMount"
                (fn []
                  (this-as this
                    (vswap! (state this) call-all did-mount)))))

    (when-not (empty? should-update)
      (gobj/set prototype "shouldComponentUpdate"
                (fn [next-props next-state]
                  (this-as this
                    (let [old-state @(state this)
                          new-state @(gobj/get next-state ":rum/state")]
                      (or (some #(% old-state new-state) should-update) false))))))

    (gobj/set prototype "render"
              (fn []
                (this-as this
                  (let [state (state this)
                        [dom next-state] (wrapped-render @state)]
                    (vreset! state next-state)
                    dom))))

    (when-not (empty? make-snapshot)
      (gobj/set prototype "getSnapshotBeforeUpdate"
                (fn [prev-props prev-state]
                  (let [state  @(gobj/get prev-state ":rum/state")]
                    (call-all state make-snapshot)))))

    (when-not (empty? did-update)
      (gobj/set prototype "componentDidUpdate"
                (fn [_ _ snapshot]
                  (this-as this
                    (vswap! (state this) call-all did-update snapshot)))))

    (when-not (empty? did-catch)
      (gobj/set prototype "componentDidCatch"
                (fn [error info]
                  (this-as this
                    (vswap! (state this) call-all did-catch error {:rum/component-stack (gobj/get info "componentStack")})
                    (.forceUpdate this)))))

    (gobj/set prototype "componentWillUnmount"
              (fn []
                (this-as this
                  (when-not (empty? will-unmount)
                    (vswap! (state this) call-all will-unmount))
                  (gobj/set this ":rum/unmounted?" true))))

    (extend! prototype class-props)
    (extend! ctor static-props)
    (gobj/set ctor "displayName" display-name)
    ctor))

(defn build-class-ctor
  [render mixins display-name]
  (let [class (build-class render mixins display-name)
        keyfn (first (collect :key-fn mixins))
        ctor  (if (some? keyfn)
                (fn [args]
                  (let [props #js {":rum/initial-state" {:rum/args args}
                                   "key" (apply keyfn args)}]
                    (js/React.createElement class props)))
                (fn [args]
                  (let [props #js { ":rum/initial-state" {:rum/args args}}]
                    (js/React.createElement class props))))]
    (with-meta ctor {:rum/class class})))

(defn build-fn-ctor
  [render-body display-name]
  (let [class (fn [props]
                (prn "build-fn-ctor" props)
                (apply render-body (gobj/get props ":rum/args")))
        _     (gobj/set class "displayName" display-name)
        ctor  (fn [& args] (js/React.createElement class #js {":rum/args" args}))]
    (with-meta ctor {:rum/class class})))

(defn build-lazy-ctor
  [builder render mixins display-name]
  (let [ctor (delay (builder render mixins display-name))]
    (fn [& args] (@ctor args))))

(defn build-defc
  [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (:rum/args state)) state])]
    (build-class-ctor render mixins display-name)))

(defn build-defcs [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body state (:rum/args state)) state])]
    (build-class-ctor render mixins display-name)))

(defn build-defcc [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (:rum/react-component state) (:rum/args state)) state])]
    (build-class-ctor render mixins display-name)))

;; render queue

(def ^:private schedule
  (or (and (exists? js/window)
           (or js/window.requestAnimationFrame
               js/window.webkitRequestAnimationFrame
               js/window.mozRequestAnimationFrame
               js/window.msRequestAnimationFrame))
    #(js/setTimeout % 16)))

(def ^:private batch
  (or (when (exists? js/ReactNative) js/ReactNative.unstable_batchedUpdates)
      (when (exists? js/ReactDOM) js/ReactDOM.unstable_batchedUpdates)
      (fn [f a] (f a))))

(def ^:private empty-queue [])
(def ^:private render-queue (volatile! empty-queue))

(defn- render-all [queue]
  (doseq [comp queue
          :when (not (gobj/get comp ":rum/unmounted?"))]
    (.forceUpdate comp)))

(defn- render []
  (let [queue @render-queue]
    (vreset! render-queue empty-queue)
    (batch render-all queue)))

(defn request-render
  "Schedules react component to be rendered on next animation frame."
  [component]
  (when (empty? @render-queue)
    (schedule render))
  (vswap! render-queue conj component))

(defn mount
  "Add element to the DOM tree. Idempotent. Subsequent mounts will just update element."
  [element node]
  (js/ReactDOM.render element node)
  nil)

(defn unmount
  "Removes component from the DOM tree."
  [node]
  (js/ReactDOM.unmountComponentAtNode node))

(defn hydrate
  "Same as [[mount]] but must be called on DOM tree already rendered by a server via [[render-html]]."
  [element node]
  (js/ReactDOM.hydrate element node))

(defn portal
  "Render `element` in a DOM `node` that is ouside of current DOM hierarchy."
  [element node]
  (js/ReactDOM.createPortal element node))

;; initialization

(defn with-key
  "Adds React key to element.

   ```
   (rum/defc label [text] [:div text])

   (-> (label)
       (rum/with-key \"abc\")
       (rum/mount js/document.body))
   ```"
  [element key]
  (js/React.cloneElement element #js { "key" key } nil))

(defn with-ref
  "Adds React ref (string or callback) to element.

   ```
   (rum/defc label [text] [:div text])

   (-> (label)
       (rum/with-ref \"abc\")
       (rum/mount js/document.body))
   ```"
  [element ref]
  (js/React.cloneElement element #js { "ref" ref } nil))

(defn dom-node
  "Given state, returns top-level DOM node of component. Call it
  during lifecycle callbacks. Can’t be called during render."
  [state]
  (js/ReactDOM.findDOMNode (:rum/react-component state)))

(defn ref
  "Given state and ref handle, returns React component."
  [state key]
  (-> state :rum/react-component (gobj/get "refs") (gobj/get (name key))))

(defn ref-node
  "Given state and ref handle, returns DOM node associated with ref."
  [state key]
  (js/ReactDOM.findDOMNode (ref state (name key))))

;; static mixin

(def static
  "Mixin. Will avoid re-render if none of component’s arguments have
  changed. Does equality check (`=`) on all arguments.

   ```
   (rum/defc label < rum/static
     [text]
     [:div text])

   (rum/mount (label \"abc\") js/document.body)

   ;; def != abc, will re-render
   (rum/mount (label \"def\") js/document.body)

   ;; def == def, won’t re-render
   (rum/mount (label \"def\") js/document.body)
   ```"
  {:should-update (fn [old-state new-state]
                    (not= (:rum/args old-state) (:rum/args new-state)))})


;; local mixin

(defn local
  "Mixin constructor. Adds an atom to component’s state that can be used
  to keep stuff during component’s lifecycle. Component will be
  re-rendered if atom’s value changes. Atom is stored under
  user-provided key or under `:rum/local` by default.

   ```
   (rum/defcs counter < (rum/local 0 :cnt)
     [state label]
     (let [*cnt (:cnt state)]
       [:div {:on-click (fn [_] (swap! *cnt inc))}
         label @*cnt]))

   (rum/mount (counter \"Click count: \"))
   ```"
  ([] (local {} :rum/local))
  ([initial] (local initial :rum/local))
  ([initial key]
   {:init
    (fn [state props]
      (let [local-state (atom initial)
            component   (:rum/react-component state)]
        (add-watch local-state key #(request-render component))
        (assoc state key local-state)))}))


;; reactive mixin

(def ^:private ^:dynamic *reactions*)

(def reactive
  "Mixin. Works in conjunction with [[react]].

   ```
   (rum/defc comp < rum/reactive
     [*counter]
     [:div (rum/react counter)])

   (def *counter (atom 0))
   (rum/mount (comp *counter) js/document.body)
   (swap! *counter inc) ;; will force comp to re-render
   ```"
  {:init
   (fn [state props]
     (assoc state :rum.reactive/key (random-uuid)))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (binding [*reactions* (volatile! #{})]
         (let [comp             (:rum/react-component state)
               old-reactions    (:rum.reactive/refs state #{})
               [dom next-state] (render-fn state)
               new-reactions    @*reactions*
               key              (:rum.reactive/key state)]
           (doseq [ref old-reactions]
             (when-not (contains? new-reactions ref)
               (remove-watch ref key)))
           (doseq [ref new-reactions]
             (when-not (contains? old-reactions ref)
               (add-watch ref key
                          (fn [_ _ _ _]
                            (request-render comp)))))
           [dom (assoc next-state :rum.reactive/refs new-reactions)]))))
   :will-unmount
   (fn [state]
     (let [key (:rum.reactive/key state)]
       (doseq [ref (:rum.reactive/refs state)]
         (remove-watch ref key)))
     (dissoc state :rum.reactive/refs :rum.reactive/key)) })

(defn react
  "Works in conjunction with [[reactive]] mixin. Use this function
  instead of `deref` inside render, and your component will subscribe
  to changes happening to the derefed atom."
  [ref]
  (assert *reactions* "rum.core/react is only supported in conjunction with rum.core/reactive")
  (vswap! *reactions* conj ref)
  @ref)
