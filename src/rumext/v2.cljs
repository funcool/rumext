;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) Andrey Antukh <niwi@niwi.nz>

(ns rumext.v2
  (:refer-clojure :exclude [ref deref])
  (:require-macros [rumext.v2 :refer [defc fnc]])
  (:require
   ["react" :as react]
   ["react-dom/client" :as rdom]
   ["react/jsx-runtime" :as jsxrt]
   [shadow.lazy]
   [cljs.core :as c]
   [goog.functions :as gf]
   [rumext.v2.util :as util]))

(def ^:const undefined (js* "(void 0)"))

(def browser-context?
  "A boolean var, indicates if the current code is running on browser main thread or not."
  (exists? js/window))

(def Component
  "The `react.Component` class"
  react/Component)

(def Fragment
  "The `react.Fragment class"
  react/Fragment)

(def Profiler
  "The `react.Profiler` class"
  react/Profiler)

(def Suspense
  "The `react.Suspense` class"
  react/Suspense)

(extend-type cljs.core.UUID
  INamed
  (-name [this] (js* "\"\" + ~{}" this))
  (-namespace [_] ""))

(def ^:no-doc ^function jsx jsxrt/jsx)
(def ^:no-doc ^function jsxs jsxrt/jsxs)

(defn merge-props
  [props1 props2]
  (js/Object.assign #js {} props1 props2))

(def ^function forward-ref
  "lets your component expose a DOM node to parent component with a ref."
  react/forwardRef)

;; --- Main Api

(def ^function mount
  "Add element to the DOM tree. Idempotent. Subsequent mounts will
  just update element."
  rdom/render)

(def ^function unmount
  "Removes component from the DOM tree."
  rdom/unmountComponentAtNode)

(def ^function portal
  "Render `element` in a DOM `node` that is ouside of current DOM hierarchy."
  rdom/createPortal)

(def ^function create-root
  "Creates react root"
  rdom/createRoot)

(def hydrate-root
  "Lets you display React components inside a browser DOM node whose
  HTML content was previously generated by react-dom/server"
  rdom/hydrateRoot)

(defn render!
  [root element]
  (.render ^js root element))

(defn unmount!
  "Removes component from the DOM tree."
  [root]
  (.unmount ^js root))

(def ^function create-ref react/createRef)

(defn ref-val
  "Given state and ref handle, returns React component."
  [ref]
  (unchecked-get ref "current"))

(defn set-ref-val!
  [ref val]
  (unchecked-set ref "current" val)
  val)

(def ^function lazy
  "A helper for creating lazy loading components."
  react/lazy)

;; --- Context API

(def ^function create-context
  "Create a react context"
  react/createContext)

(defn provider
  "Get the current provider for specified context"
  [ctx]
  (unchecked-get ctx "Provider"))

;; --- Raw Hooks

(def ^function useId
  "The `react.useId` hook function"
  react/useId)

(def ^function useRef
  "The `react.useRef` hook function"
  react/useRef)

(def ^function useState
  "The `react.useState` hook function"
  react/useState)

(def ^function useEffect
  "The `react.useEffect` hook function"
  react/useEffect)

(def ^function useInsertionEffect
  "The react.useInsertionEffect` hook function"
  react/useInsertionEffect)

(def ^function useLayoutEffect
  "The `react.useLayoutEffect` hook function"
  react/useLayoutEffect)

(def ^function useDeferredValue
  "The `react.useDeferredValue hook function"
  react/useDeferredValue)

(def ^function useMemo
  "The `react.useMemo` hook function"
  react/useMemo)

(def ^function useCallback
  "The `react.useCallback` hook function"
  react/useCallback)

(def ^function useContext
  "The `react.useContext` hook function"
  react/useContext)

(def ^function useTransition
  "The `react.useTransition` hook function"
  react/useTransition)

;; --- Hooks

(defprotocol ^:no-doc IDepsAdapter
  (^:no-doc adapt [o] "adapt dep if proceed"))

(extend-protocol IDepsAdapter
  default
  (adapt [o] o)

  cljs.core.UUID
  (adapt [o] (.toString ^js o))

  cljs.core.Keyword
  (adapt [o] (.toString ^js o)))

;; "A convenience function that translates the list of arguments into a
;; valid js array for use in the deps list of hooks.

(defn deps
  "A helper for creating hook deps array, that handles some
  adaptations for clojure specific data types such that UUID and
  keywords"
  ([] #js [])
  ([a] #js [(adapt a)])
  ([a b] #js [(adapt a) (adapt b)])
  ([a b c] #js [(adapt a) (adapt b) (adapt c)])
  ([a b c d] #js [(adapt a) (adapt b) (adapt c) (adapt d)])
  ([a b c d e] #js [(adapt a) (adapt b) (adapt c) (adapt d) (adapt e)])
  ([a b c d e f] #js [(adapt a) (adapt b) (adapt c) (adapt d) (adapt e) (adapt f)])
  ([a b c d e f g] #js [(adapt a) (adapt b) (adapt c) (adapt d) (adapt e) (adapt f) (adapt g)])
  ([a b c d e f g h] #js [(adapt a) (adapt b) (adapt c) (adapt d) (adapt e) (adapt f) (adapt g) (adapt h)])
  ([a b c d e f g h & rest] (into-array (map adapt (into [a b c d e f g h] rest)))))

(def ^function use-ref
  "A lisp-case alias for `useRef`"
  react/useRef)

(def ^function use-ctx
  "A lisp-case short alias for the `useContext` hook function"
  react/useContext)

(def ^function use-id
  "A lisp-case alias fro `useId` hook function"
  react/useId)

(def ^function start-transition
  "An alias for react.startTransition function"
  react/startTransition)

(def noop (constantly nil))

(defn use-effect
  "A rumext variant of the `useEffect` hook function with order of
  arguments inverted"
  ([f] (use-effect #js [] f))
  ([deps f]
   (useEffect #(let [r (^function f)] (if (fn? r) r noop)) deps)))

(defn use-insertion-effect
  "A rumext variant of the `useInsertionEffect` hook function with order
  of arguments inverted"
  ([f] (use-insertion-effect #js [] f))
  ([deps f]
   (useInsertionEffect #(let [r (^function f)] (if (fn? r) r noop)) deps)))

(defn use-layout-effect
  "A rumext variant of the `useLayoutEffect` hook function with order
  of arguments inverted"
  ([f] (use-layout-effect #js [] f))
  ([deps f]
   (useLayoutEffect #(let [r (^function f)] (if (fn? r) r noop)) deps)))

(defn use-ssr-effect
  "An EXPERIMENTAL use-effect version that detects if we are in a NON
  browser context and runs the effect fn inmediatelly."
  [deps effect-fn]
  (if ^boolean browser-context?
    (use-effect deps effect-fn)
    (let [ret (effect-fn)]
      (when (fn? ret)
        (ret)))))

(defn use-memo
  "A rumext variant of the `useMemo` hook function with order
  of arguments inverted"
  ([f] (useMemo f #js []))
  ([deps f] (useMemo f deps)))

(defn use-transition
  "A rumext version of the `useTransition` hook function. Returns a
  function object that implements the IPending protocol for check the
  state of the transition."
  []
  (let [tmp        (useTransition)
        is-pending (aget tmp 0)
        start-fn   (aget tmp 1)]
    (use-memo
     #js [is-pending]
     (fn []
       (specify! (fn [cb-fn]
                   (^function start-fn cb-fn))
         cljs.core/IPending
         (-realized? [_] (not ^boolean is-pending)))))))

(defn use-callback
  "A rumext variant of the `useCallback` hook function with order
  of arguments inverted"
  ([f] (useCallback f #js []))
  ([deps f] (useCallback f deps)))

(defn use-fn
  "A convenience short alias for `use-callback`"
  ([f] (useCallback f #js []))
  ([deps f] (useCallback f deps)))

(defn deref
  "A rumext hook for deref and watch an atom or atom like object. It
  internally uses the react.useSyncExternalSource API"
  [iref]
  (let [state     (use-ref (c/deref iref))
        key       (use-id)
        get-state (use-fn #js [state] #(unchecked-get state "current"))
        subscribe (use-fn #js [iref key]
                          (fn [listener-fn]
                            (unchecked-set state "current" (c/deref iref))
                            (add-watch iref key (fn [_ _ _ newv]
                                                  (unchecked-set state "current" newv)
                                                  (^function listener-fn)))
                            #(remove-watch iref key)))
        snapshot  (use-fn #js [iref] #(c/deref iref))]
    (react/useSyncExternalStore subscribe get-state snapshot)))

(defn use-state
  "A rumext variant of `useState`. Returns an object that implements
  the Atom protocols."
  ([] (use-state nil))
  ([initial]
   (let [tmp    (useState initial)
         state  (aget tmp 0)
         update (aget tmp 1)]
     (use-memo
      #js [state]
      (fn []
        (reify
          c/IReset
          (-reset! [_ value]
            (^function update value))

          c/ISwap
          (-swap! [self f]
            (^function update f))
          (-swap! [self f x]
            (^function update #(f % x)))
          (-swap! [self f x y]
            (^function update #(f % x y)))
          (-swap! [self f x y more]
            (^function update #(apply f % x y more)))

          c/IDeref
          (-deref [_] state)))))))

(defn use-var
  "A rumext custom hook that uses `useRef` under the hood. Returns an
  object that implements the Atom protocols.  The updates does not
  trigger rerender."
  ([] (use-var nil))
  ([initial]
   (let [ref (useRef initial)]
     (use-memo
      #(specify! (fn [val] (set-ref-val! ref val))
         c/IReset
         (-reset! [_ new-value]
           (set-ref-val! ref new-value))

         c/ISwap
         (-swap!
           ([self f]
            (set-ref-val! ref (f (ref-val ref))))
           ([self f x]
            (set-ref-val! ref (f (ref-val ref) x)))
           ([self f x y]
            (set-ref-val! ref (f (ref-val ref) x y)))
           ([self f x y more]
            (set-ref-val! ref (apply f (ref-val ref) x y more))))

         c/IDeref
         (-deref [self] (ref-val ref)))))))

;; --- Other API

(defn element
  "Create a react element. This is a public API for the internal `jsx`
  function"
  ([klass]
   (jsx klass #js {} undefined))
  ([klass props]
   (let [props (cond
                 (object? props) ^js props
                 (map? props) (util/map->obj props)
                 :else (throw (ex-info "Unexpected props" {:props props})))]
     (jsx klass props undefined))))

;; --- Higher-Order Components

(defn memo
  "High order component for memoizing component props. Is a rumext
  variant of React.memo what accepts a value comparator
  function (instead of props comparator)"
  ([component] (react/memo component))
  ([component eq?]
   (react/memo component #(util/props-equals? eq? %1 %2))))

(def ^function memo'
  "A raw variant of React.memo."
  react/memo)

(defn catch
  "High order component that adds an error boundary"
  [component {:keys [fallback on-error]}]
  (let [constructor
        (fn [props]
          (this-as this
            (unchecked-set this "state" #js {})
            (.call Component this props)))

        did-catch
        (fn [error info]
          (when (fn? on-error)
            (on-error error info)))

        derive-state
        (fn [error]
          #js {:error error})

        render
        (fn []
          (this-as this
            (let [state (unchecked-get this "state")
                  props (unchecked-get this "props")
                  error (unchecked-get state "error")]
              (if error
                (jsx fallback #js {:error error} undefined)
                (jsx component props undefined)))))

        _ (goog/inherits constructor Component)
        prototype (unchecked-get constructor "prototype")]

    (unchecked-set constructor "displayName" "ErrorBoundary")
    (unchecked-set constructor "getDerivedStateFromError" derive-state)
    (unchecked-set prototype "componentDidCatch" did-catch)
    (unchecked-set prototype "render" render)
    constructor))

(def ^:private schedule
  (or (and (exists? js/window) js/window.requestAnimationFrame)
      #(js/setTimeout % 16)))

(defn deferred
  "A higher-order component that just deffers the first render to the next tick"
  ([component] (deferred component schedule))
  ([component sfn]
   (fnc deferred
     {::wrap-props false}
     [props]
     (let [tmp        (useState false)
           render?    (aget tmp 0)
           set-render (aget tmp 1)]
       (use-effect (fn [] (^function sfn #(^function set-render true))))
       (when ^boolean render?
         [:> component props])))))

(defn throttle
  "A higher-order component that throttles the rendering"
  [component ms]
  (fnc throttle
    {::wrap-props false}
    [props]
    (let [tmp       (useState props)
          state     (aget tmp 0)
          set-state (aget tmp 1)

          ref       (useRef false)
          render    (useMemo
                     #(gf/throttle
                       (fn [v]
                         (when-not ^boolean (ref-val ref)
                           (^function set-state v)))
                       ms)
                     #js [])]
      (useEffect #(^function render props) #js [props])
      (useEffect #(fn [] (set-ref-val! ref true)) #js [])
      [:> component state])))

(defn use-debounce
  "A rumext custom hook that debounces the value changes"
  [ms value]
  (let [[state update-fn] (useState value)
        update-fn (useMemo #(gf/debounce update-fn ms) #js [ms])]
    (useEffect #(update-fn value) #js [value])
    state))

(defn use-equal-memo
  "A rumext custom hook that preserves object identity through using a
  `=` (value equality). Optionally, you can provide your own
  function."
  ([val]
   (let [ref (use-ref nil)]
     (when-not (= (ref-val ref) val)
       (set-ref-val! ref val))
     (ref-val ref)))
  ([eqfn val]
   (let [ref (use-ref nil)]
     (when-not (eqfn (ref-val ref) val)
       (set-ref-val! ref val))
     (ref-val ref))))

(def ^function use-deferred
  "A lisp-case shorter alias for `useDeferredValue`"
  react/useDeferredValue)

(defn use-previous
  "A rumext custom hook that returns a value from previous render"
  [value]
  (let [ref (use-ref value)]
    (use-effect #js [value] #(set-ref-val! ref value))
    (ref-val ref)))

(defn use-update-ref
  "A rumext custom hook that updates the ref value if the value changes"
  [value]
  (let [ref (use-ref value)]
    (use-effect #js [value] #(set-ref-val! ref value))
    ref))

(defn use-ref-fn
  "A rumext custom hook that returns a stable callback pointer what
  calls the interned callback. The interned callback will be
  automatically updated on each render if the reference changes and
  works as noop if the pointer references to nil value."
  [f]
  (let [ptr (use-ref nil)]
    (use-effect #js [f] #(set-ref-val! ptr f))
    (use-fn (fn []
              (let [f    (ref-val ptr)
                    args (js-arguments)]
                (when (some? f)
                  (.apply f args)))))))


