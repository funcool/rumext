;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) Andrey Antukh <niwi@niwi.nz>

(ns rumext.alpha
  (:refer-clojure :exclude [ref deref])
  (:require-macros [rumext.alpha :refer [defc fnc]])
  (:require
   ["react" :as react]
   ["react-dom" :as rdom]
   ["react/jsx-runtime" :as jsxrt]
   [cljs.core :as c]
   [goog.functions :as gf]
   [rumext.util :as util]))

(def ^:const undefined (js* "(void 0)"))

(def Component react/Component)
(def Fragment react/Fragment)
(def Profiler react/Profiler)

(extend-type cljs.core.UUID
  INamed
  (-name [this] (js* "\"\" + ~{}" this))
  (-namespace [_] ""))

(defn jsx
  ([type props maybe-key]
   (jsxrt/jsx type props maybe-key))
  ([type props maybe-key children]
   (let [props (js/Object.assign #js {:children children} props)]
     (jsxrt/jsx type props maybe-key))))

(defn jsxs
  ([type props maybe-key]
   (jsxrt/jsxs type props maybe-key))
  ([type props maybe-key children]
   (let [props (js/Object.assign #js {:children children} props)]
     (jsxrt/jsxs type props maybe-key))))

(defn forward-ref
  [component]
  (react/forwardRef component))

;; --- Main Api

(defn mount
  "Add element to the DOM tree. Idempotent. Subsequent mounts will
  just update element."
  [element node]
  (rdom/render element node)
  nil)

(defn unmount
  "Removes component from the DOM tree."
  [node]
  (rdom/unmountComponentAtNode node))

(defn portal
  "Render `element` in a DOM `node` that is ouside of current DOM hierarchy."
  [element node]
  (rdom/createPortal element node))

(defn create-ref
  []
  (react/createRef))

(defn ref-val
  "Given state and ref handle, returns React component."
  [ref]
  (unchecked-get ref "current"))

(defn set-ref-val!
  [ref val]
  (unchecked-set ref "current" val)
  val)

;; --- Context API

(defn create-context
  ([]
   (react/createContext nil))
  ([value]
   (react/createContext value)))

(defn provider
  [ctx]
  (unchecked-get ctx "Provider"))

;; --- Raw Hooks

(defn useRef
  [initial]
  (react/useRef initial))

(defn useState
  [initial]
  (react/useState initial))

(defn useEffect
  [f deps]
  (react/useEffect f deps))

(defn useMemo
  [f deps]
  (react/useMemo f deps))

(defn useCallback
  [f deps]
  (react/useCallback f deps))

(defn useLayoutEffect
  [f deps]
  (react/useLayoutEffect f deps))

(defn useContext
  [ctx]
  (react/useContext ctx))

;; --- Hooks

(defprotocol IDepsAdapter
  (adapt [o] "adapt dep if proceed"))

(extend-protocol IDepsAdapter
  default
  (adapt [o] o)

  cljs.core.UUID
  (adapt [o] (.toString ^js o))

  cljs.core.Keyword
  (adapt [o] (.toString ^js o)))

;; "A convenience function that translates the list of arguments into a
;; valid js array for use in the deps list of hooks.

(defn  deps
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

;; The cljs version of use-ref and use-ctx is identical to the raw (no
;; customizations/adaptations needed)

(def use-ref useRef)
(def use-ctx useContext)

(defn use-effect
  ([f] (use-effect #js [] f))
  ([deps f]
   (useEffect #(let [r (^js f)] (if (fn? r) r identity)) deps)))

(defn use-layout-effect
  ([f] (use-layout-effect #js [] f))
  ([deps f]
   (useLayoutEffect #(let [r (^js f)] (if (fn? r) r identity)) deps)))

(defn use-memo
  ([f] (useMemo f #js []))
  ([deps f] (useMemo f deps)))

(defn use-callback
  ([f] (useCallback f #js []))
  ([deps f] (useCallback f deps)))

(defn use-fn
  "A convenient alias to useCallback"
  ([f] (useCallback f #js []))
  ([deps f] (useCallback f deps)))

(defn deref
  [iref]
  (let [tmp       (useState #(c/deref iref))
        state     (aget tmp 0)
        set-state (aget tmp 1)
        key       (useMemo
                   #(let [key (js/Symbol "rumext.alpha/deref")]
                      (add-watch iref key (fn [_ _ _ newv]
                                            (^js set-state newv)))
                      key)
                   #js [iref])]

    (useEffect #(fn []
                  (remove-watch iref key))
               #js [iref key])

    state))

(defn use-state
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
            (update value))

          c/ISwap
          (-swap! [self f]
            (update f))
          (-swap! [self f x]
            (update #(f % x)))
          (-swap! [self f x y]
            (update #(f % x y)))
          (-swap! [self f x y more]
            (update #(apply f % x y more)))

          c/IDeref
          (-deref [_] state)))))))

(defn use-var
  "A custom hook for define mutable variables that persists
  on renders (based on useRef hook)."
  ([] (use-var nil))
  ([initial]
   (let [ref (useRef initial)]
     (use-memo
      #js []
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
  ([klass]
   (jsx klass #js {} undefined))
  ([klass props]
   (let [props (cond
                 (object? props) props
                 (map? props) (util/map->obj props)
                 :else (throw (ex-info "Unexpected props" {:props props})))]
     (jsx klass props undefined))))

;; --- Higher-Order Components

(defn memo'
  "A raw variant of React.memo."
  [component equals?]
  (react/memo component equals?))

(defn memo
  ([component] (react/memo component))
  ([component eq?]
   (react/memo component #(util/props-equals? eq? %1 %2))))

(defn catch
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
  ([component] (deferred component schedule))
  ([component sfn]
   (fnc test
     {::wrap-props false}
     [props]
     (let [tmp (useState false)
           ^boolean render? (aget tmp 0)
           ^js set-render (aget tmp 1)]
       (use-effect (fn [] (^js sfn #(set-render true))))
       (when render?
         (jsx component props undefined))))))

(defn throttle
  [component ms]
  (fn [props]
    (let [tmp       (useState props)
          state     (aget tmp 0)
          set-state (aget tmp 1)

          ref       (use-ref false)
          render    (use-memo #(gf/throttle
                                (fn [v]
                                  (when-not (ref-val ref)
                                    (^js set-state v)))
                                ms))]
      (use-effect nil #(render props))
      (use-effect (fn [] #(set-ref-val! ref true)))
      (component state))))

(defn check-props
  "Utility function to use with `memo'`.
  Will check the `props` keys to see if they are equal.

  Usage:

  (mf/defc my-component
    {::mf/wrap [#(mf/memo' % (checkprops [\"prop1\" \"prop2\"]))]}
    [props]
  )"

  ([props] (check-props props =))
  ([props eqfn?]
   (fn [np op]
     (every? #(eqfn? (unchecked-get np %)
                     (unchecked-get op %))
             props))))
