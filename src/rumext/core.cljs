;; This Source Code Form is subject to the terms of the Eclipse Public
;; License - v 1.0
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>
;;
;; Many parts of this code is derived from https://github.com/tonsky/rum

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

(defn get-local-state
  "Given React component, returns Rum state associated with it."
  [comp]
  (gobj/get (.-state comp) ":rumext.core/state"))

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
        did-update     (collect* [:did-update               ;; state snapshot -> state
                                  :after-render] mixins)    ;; state -> state
        make-snapshot  (collect :make-snapshot mixins)      ;; state -> snapshot
        did-catch      (collect   :did-catch mixins)        ;; state error info -> state
        will-unmount   (collect   :will-unmount mixins)     ;; state -> state
        class-props    (reduce merge (collect :class-properties mixins))  ;; custom prototype properties and methods
        static-props   (reduce merge (collect :static-properties mixins)) ;; custom static properties and methods

        ctor           (fn [props]
                         (this-as this
                           (let [lprops (unchecked-get props ":rumext.core/props")
                                 lstate (-> {::props lprops ::react-component this}
                                            (call-all init lprops))]
                             (unchecked-set this "state" #js {":rumext.core/state" (volatile! lstate)})
                             (.call js/React.Component this props))))
        _              (goog/inherits ctor js/React.Component)
        prototype      (unchecked-get ctor "prototype")]

    (extend! prototype class-props)
    (extend! ctor static-props)

    (unchecked-set ctor "displayName" display-name)

    (unchecked-set ctor "getDerivedStateFromProps"
                   (fn [props state]
                     (let [lstate  @(gobj/get state ":rumext.core/state")
                           nprops  (gobj/get props ":rumext.core/props")
                           nstate  (merge lstate {::props nprops})
                           nstate  (reduce #(%2 %1) nstate derive-state)]
                       ;; allocate new volatile
                       ;; so that we can access both old and new states in shouldComponentUpdate
                       #js {":rumext.core/state" (volatile! nstate)})))

    (unchecked-set prototype "render"
                   (fn []
                     (this-as this
                       (let [lstate (get-local-state this)
                             [dom nstate] (wrapped-render @lstate)]
                         (vreset! lstate nstate)
                         dom))))

    (when-not (empty? did-mount)
      (unchecked-set prototype "componentDidMount"
                     (fn []
                       (this-as this
                         (let [lstate (get-local-state this)]
                           (vswap! lstate call-all did-mount))))))

    (when-not (empty? should-update)
      (unchecked-set prototype "shouldComponentUpdate"
                     (fn [next-props next-state]
                       (this-as this
                         (let [lstate @(get-local-state this)
                               nstate @(gobj/get next-state ":rumext.core/state")]
                           (or (some #(% lstate nstate) should-update) false))))))

    (when-not (empty? make-snapshot)
      (unchecked-set prototype "getSnapshotBeforeUpdate"
                     (fn [prev-props prev-state]
                       (let [lstate  @(gobj/get prev-state ":rumext.core/state")]
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

    (unchecked-set prototype "componentWillUnmount"
                   (fn []
                     (this-as this
                       (let [lstate (get-local-state this)]
                         (when-not (empty? will-unmount)
                           (vswap! lstate call-all will-unmount))
                         (gobj/set this ":rumext.core/unmounted?" true)))))

    ctor))

(defn build-lazy-ctor
  [builder render mixins display-name]
  (let [ctor (delay (builder render mixins display-name))]
    (fn [props] (@ctor props))))

(defn build-def
  [render-body mixins display-name]
  (let [render (fn [state] [(render-body state (::props state)) state])
        klass (build-class render mixins display-name)
        keyfn (first (collect :key-fn mixins))]
    (if (some? keyfn)
      #(js/React.createElement klass #js {":rumext.core/props" %1 "key" (keyfn %1)})
      #(js/React.createElement klass #js {":rumext.core/props" %1}))))

(defn build-legacy-fn-ctor
  [render-body display-name]
  (let [klass (fn [props] (apply render-body (gobj/get props ":rumext.core/props")))]
    (gobj/set klass "displayName" display-name)
    (fn [& args]
      (js/React.createElement klass #js {":rumext.core/props" args}))))

(defn build-legacy-lazy-ctor
  [builder render mixins display-name]
  (let [ctor (delay (builder render mixins display-name))]
    (fn [& args] (@ctor args))))

(defn- build-legacy-elem-ctor
  "The common code used by build-defc, -defcs and -defcc."
  [render mixins display-name]
  (let [class (build-class render mixins display-name)
        keyfn (first (collect :key-fn mixins))]
    (if (some? keyfn)
      #(js/React.createElement class #js {":rumext.core/props" %1 "key" (apply keyfn %1)})
      #(js/React.createElement class #js {":rumext.core/props" %1}))))

(defn build-defc
  [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (::props state)) state])]
    (build-legacy-elem-ctor render mixins display-name)))

(defn build-defcs [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body state (::props state)) state])]
    (build-legacy-elem-ctor render mixins display-name)))

(defn build-defcc [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (::react-component state) (::props state)) state])]
    (build-legacy-elem-ctor render mixins display-name)))

(defn request-render
  "Schedules react component to be rendered on next animation frame."
  [component]
  (.forceUpdate component))

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
   (rmx/defc label [text] [:div text])

   (-> (label)
       (rmx/with-key \"abc\")
       (rmx/mount js/document.body))
   ```"
  [element key]
  (js/React.cloneElement element #js { "key" key } nil))

(defn with-ref
  "Adds React ref (string or callback) to element.

   ```
   (rmx/defc label [text] [:div text])

   (-> (label)
       (rmx/with-ref \"abc\")
       (rmx/mount js/document.body))
   ```"
  [element ref]
  (js/React.cloneElement element #js { "ref" ref } nil))

(defn dom-node
  "Given state, returns top-level DOM node of component. Call it
  during lifecycle callbacks. Can’t be called during render."
  [state]
  (js/ReactDOM.findDOMNode (::react-component state)))

(defn create-ref
  []
  (js/React.createRef))

(defn ref-val
  "Given state and ref handle, returns React component."
  [ref]
  (gobj/get ref "current"))

(defn ref-node
  "Given state and ref handle, returns DOM node associated with ref."
  ([ref]
   (js/ReactDOM.findDOMNode (ref-val ref)))
  ([state key]
   (let [ref (-> state ::react-component (gobj/get "refs") (gobj/get (name key)))]
     (js/ReactDOM.findDOMNode ref))))


;; static mixin

(def static
  "Mixin. Will avoid re-render if none of component’s arguments have
  changed. Does equality check (`=`) on all arguments.

   ```
   (rmx/defc label < rmx/static
     [text]
     [:div text])

   (rmx/mount (label \"abc\") js/document.body)

   ;; def != abc, will re-render
   (rmx/mount (label \"def\") js/document.body)

   ;; def == def, won’t re-render
   (rmx/mount (label \"def\") js/document.body)
   ```"
  {:should-update (fn [old-state new-state]
                    (not= (::props old-state) (::props new-state)))})


;; local mixin

(defn local
  "Mixin constructor. Adds an atom to component’s state that can be used
  to keep stuff during component’s lifecycle. Component will be
  re-rendered if atom’s value changes. Atom is stored under
  user-provided key or under `:rumext.core/local` by default.

   ```
   (rmx/defcs counter < (rmx/local 0 :cnt)
     [state label]
     (let [*cnt (:cnt state)]
       [:div {:on-click (fn [_] (swap! *cnt inc))}
         label @*cnt]))

   (rmx/mount (counter \"Click count: \"))
   ```"
  ([] (local {} ::local))
  ([initial] (local initial ::local))
  ([initial key]
   {:init
    (fn [state props]
      (let [lstate (atom initial)
            component (::react-component state)]
        (add-watch lstate key #(request-render component))
        (assoc state key lstate)))}))


;; reactive mixin

(def ^:private ^:dynamic *reactions*)

(def reactive
  "Mixin. Works in conjunction with [[react]].

   ```
   (rmx/defc comp < rmx/reactive
     [*counter]
     [:div (rmx/react counter)])

   (def *counter (atom 0))
   (rmx/mount (comp *counter) js/document.body)
   (swap! *counter inc) ;; will force comp to re-render
   ```"
  {:init
   (fn [state props]
     (assoc state ::reactions-key (random-uuid)))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (binding [*reactions* (volatile! #{})]
         (let [comp             (::react-component state)
               old-reactions    (::reactions state #{})
               [dom next-state] (render-fn state)
               new-reactions    (deref *reactions*)
               key              (::reactions-key state)]
           (doseq [ref old-reactions]
             (when-not (contains? new-reactions ref)
               (remove-watch ref key)))
           (doseq [ref new-reactions]
             (when-not (contains? old-reactions ref)
               (add-watch ref key
                          (fn [_ _ _ _]
                            (request-render comp)))))
           [dom (assoc next-state ::reactions new-reactions)]))))
   :will-unmount
   (fn [state]
     (let [key (::reactions-key state)]
       (doseq [ref (::reactions state)]
         (remove-watch ref key)))
     (dissoc state ::reactions ::reactions-key)) })

(defn react
  "Works in conjunction with [[reactive]] mixin. Use this function
  instead of `deref` inside render, and your component will subscribe
  to changes happening to the derefed atom."
  [ref]
  (assert *reactions* "rumext.core/react is only supported in conjunction with rumext.core/reactive")
  (vswap! *reactions* conj ref)
  @ref)
