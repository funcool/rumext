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
   [rumext.alpha :as ra]
   [rumext.util :as util :refer [collect collect* call-all]]))

(defn build-legacy-fn-ctor
  [render-body display-name]
  (let [klass (fn [props] (apply render-body (gobj/get props ":rumext.alpha/props")))]
    (gobj/set klass "displayName" display-name)
    (fn [& args]
      (js/React.createElement klass #js {":rumext.alpha/props" args}))))

(defn build-legacy-lazy-ctor
  [builder render mixins display-name]
  (let [ctor (delay (builder render mixins display-name))]
    (fn [& args] (@ctor args))))

(defn- build-legacy-elem
  "The common code used by build-defc, -defcs and -defcc."
  [render mixins display-name]
  (let [class (ra/build-class render mixins display-name)
        keyfn (first (collect :key-fn mixins))]
    (if (some? keyfn)
      #(js/React.createElement class #js {":rumext.alpha/props" %1 "key" (apply keyfn %1)})
      #(js/React.createElement class #js {":rumext.alpha/props" %1}))))

(defn build-defc
  [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (::ra/props state)) state])]
    (build-legacy-elem render mixins display-name)))

(defn build-defcs [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body state (::ra/props state)) state])]
    (build-legacy-elem render mixins display-name)))

(defn build-defcc [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (::ra/react-component state) (::ra/props state)) state])]
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
(def static ra/static)
(def sync-render ra/sync-render)
(def reactive ra/reactive)
(def react ra/deref)
(def react-component ra/react-component)

(defn local
  ([] (ra/local {} ::local))
  ([initial] (ra/local initial ::local))
  ([initial key] (ra/local initial key)))


(defn ref-node
  "Given state and ref handle, returns DOM node associated with ref."
  ([ref]
   (js/ReactDOM.findDOMNode (ref-val ref)))
  ([state key]
   (let [ref (-> state ::react-component (gobj/get "refs") (gobj/get (name key)))]
     (js/ReactDOM.findDOMNode ref))))
