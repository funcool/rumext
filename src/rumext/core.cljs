;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2017 Andrey Antukh <niwi@niwi.nz>

(ns rumext.core
  (:require [sablono.core :refer-macros [html]]
            [sablono.server :as server]
            [rum.core :as rum]
            [lentes.core :as l]
            [goog.dom.forms :as gforms]))

(extend-type cljs.core.UUID
  INamed
  (-name [this] (str this))
  (-namespace [_] ""))

(defn local
  ([]
   (rum/local {} :rum/local))
  ([initial]
   (rum/local initial :rum/local))
  ([key initial]
   (rum/local initial key)))

(defn lazy-component
  [builder render mixins display-name]
  (let [ctor (delay (builder render mixins display-name))]
    (fn [& args]
      (apply @ctor args))))

(def mount rum/mount)
(def static rum/static)
(def ref-node rum/ref-node)
(def dom-node rum/dom-node)
(def react rum/react)
(def reactive rum/reactive)
(def with-key rum/with-key)
(def render-html server/render)
(def render-static-html server/render-static)
