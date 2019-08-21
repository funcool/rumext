;; This Source Code Form is subject to the terms of the Eclipse Public
;; License - v 1.0
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>
;;
;; Many parts of this code is derived from https://github.com/tonsky/rum

(ns rumext.core
  "DEPRECATED: this is maintained only for backward compatibility and
  this will be removed in a near future."
  (:refer-clojure :exclude [ref])
  (:require-macros rumext.core)
  (:require
   [cljsjs.react]
   [cljsjs.react.dom]
   [goog.object :as gobj]
   [rumext.alpha :as ra]
   [rumext.util :as util :refer [collect collect* call-all]]))

(defn get-local-state
  "Given React component, returns Rum state associated with it."
  [comp]
  (-> (unchecked-get comp "state")
      (unchecked-get ":rumext.core/state")))

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
                           (let [lprops (unchecked-get props ":rumext.core/props")
                                 lstate (-> {::props lprops ::ra/react-component this}
                                            (call-all init lprops))]
                             (unchecked-set this "state" #js {":rumext.core/state" (volatile! lstate)
                                                              ":rumext.alpha/renders" -9007199254740991})
                             (.call js/React.Component this props))))
        _              (goog/inherits ctor js/React.Component)
        prototype      (unchecked-get ctor "prototype")]

    (extend! prototype class-props)
    (extend! ctor static-props)

    (unchecked-set ctor "displayName" display-name)

    (unchecked-set ctor "getDerivedStateFromProps"
                   (fn [props state]
                     (let [lstate  @(unchecked-get state ":rumext.core/state")
                           nprops  (unchecked-get props ":rumext.core/props")
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
                               nstate @(unchecked-get next-state ":rumext.core/state")]
                           (or (some #(% lstate nstate) should-update) false))))))

    (when-not (empty? make-snapshot)
      (unchecked-set prototype "getSnapshotBeforeUpdate"
                     (fn [prev-props prev-state]
                       (let [lstate  @(unchecked-get prev-state ":rumext.core/state")]
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
                         (unchecked-set this ":rumext.alpha/unmounted?" true)))))
    ctor))

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

(defn- build-legacy-elem
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
    (build-legacy-elem render mixins display-name)))

(defn build-defcs [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body state (::props state)) state])]
    (build-legacy-elem render mixins display-name)))

(defn build-defcc [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (::ra/react-component state) (::props state)) state])]
    (build-legacy-elem render mixins display-name)))

;; Backward compatibility
(def request-render ra/request-render)
(def mount ra/mount)
(def unmount ra/unmount)
(def hydrate ra/hydrate)
(def portal ra/portal)
(def with-key ra/with-key)
(def with-ref ra/with-ref)
(def dom-node ra/dom-node)
(def create-ref ra/create-ref)
(def ref-val ra/ref-val)
(def react ra/react)
(def react-component ra/react-component)

(def static
  {:should-update (fn [old-state new-state]
                    (not= (::props old-state) (::props new-state)))})

(def reactive
  {:init
   (fn [state props]
     (assoc state ::reactions-key (random-uuid)))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (binding [ra/*reactions* (volatile! #{})]
         (let [comp             (::ra/react-component state)
               old-reactions    (::reactions state #{})
               [dom next-state] (render-fn state)
               new-reactions    (deref ra/*reactions*)
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
                                    (ra/force-render comp)
                                    (ra/request-render comp)))))) new-reactions)
           [dom (assoc next-state ::reactions new-reactions)]))))
   :will-unmount
   (fn [{:keys [::reactions-key ::reactions] :as state}]
     (run! (fn [ref] (remove-watch ref reactions-key)) reactions)
     (dissoc state ::reactions ::reactions-key))
   })

(defn local
  ([] (local {} ::local))
  ([initial] (local initial ::local))
  ([initial key]
   {:init
    (fn [state props]
      (let [lstate (atom initial)
            component (::ra/react-component state)]
        (if (::sync-render state)
          (add-watch lstate key #(ra/force-render component))
          (add-watch lstate key #(ra/request-render component)))
        (assoc state key lstate)))}))

(defn ref-node
  "Given state and ref handle, returns DOM node associated with ref."
  ([ref]
   (js/ReactDOM.findDOMNode (ref-val ref)))
  ([state key]
   (let [ref (-> state ::ra/react-component (gobj/get "refs") (gobj/get (name key)))]
     (js/ReactDOM.findDOMNode ref))))
