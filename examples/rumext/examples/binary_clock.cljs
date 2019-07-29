(ns rumext.examples.binary-clock
  (:require [goog.dom :as dom]
            [rumext.alpha :as mf]
            [rumext.examples.util :as util]))

(def *bclock-renders (atom 0))

(mf/def render-count
  :mixins [mf/reactive]
  :render
  (fn [own props]
    [:div.stats "Renders: " (mf/deref *bclock-renders)]))

(mf/def bit
  :mixins [mf/memo mf/reactive]

  :after-render
  (fn [state]
    (swap! *bclock-renders inc)
    state)

  :render
  (fn [own {:keys [n b] :as props}]
    (let [color (mf/deref util/*color)]
      (if (bit-test n b)
        [:td.bclock-bit {:style {:background-color color}}]
        [:td.bclock-bit {}]))))

(mf/defc binary-clock
  []
  (let [ts   (mf/use-deref util/*clock)
        msec (mod ts 1000)
        sec  (mod (quot ts 1000) 60)
        min  (mod (quot ts 60000) 60)
        hour (mod (quot ts 3600000) 24)
        hh   (quot hour 10)
        hl   (mod  hour 10)
        mh   (quot min 10)
        ml   (mod  min 10)
        sh   (quot sec 10)
        sl   (mod  sec 10)
        msh  (quot msec 100)
        msm  (->   msec (quot 10) (mod 10))
        msl  (mod  msec 10)]
    [:table.bclock
     [:tbody
      [:tr
       [:td] [:& bit {:n hl :b 3}] [:th]
       [:td] [:& bit {:n ml :b 3}] [:th]
       [:td] [:& bit {:n sl :b 3}] [:th]
       [:& bit {:n msh :b 3}]
       [:& bit {:n msm :b 3}]
       [:& bit {:n msl :b 3}]]
      [:tr
       [:td] [:& bit {:n hl :b 2}] [:th]
       [:& bit {:n mh :b 2}]
       [:& bit {:n ml :b 2}] [:th]
       [:& bit {:n sh :b 2}]
       [:& bit {:n sl :b 2}] [:th]
       [:& bit {:n msh :b 2}]
       [:& bit {:n msm :b 2}]
       [:& bit {:n msl :b 2}]]
      [:tr
       [:& bit {:n hh :b 1}]
       [:& bit {:n hl :b 1}] [:th]
       [:& bit {:n mh :b 1}]
       [:& bit {:n ml :b 1}] [:th]
       [:& bit {:n sh :b 1}]
       [:& bit {:n sl :b 1}] [:th]
       [:& bit {:n msh :b 1}]
       [:& bit {:n msm :b 1}]
       [:& bit {:n msl :b 1}]]
      [:tr
       [:& bit {:n hh :b 0}]
       [:& bit {:n hl :b 0}] [:th]
       [:& bit {:n mh :b 0}]
       [:& bit {:n ml :b 0}] [:th]
       [:& bit {:n sh :b 0}]
       [:& bit {:n sl :b 0}] [:th]
       [:& bit {:n msh :b 0}]
       [:& bit {:n msm :b 0}]
       [:& bit {:n msl :b 0}]]
      [:tr
       [:th hh]
       [:th hl]
       [:th]
       [:th mh]
       [:th ml]
       [:th]
       [:th sh]
       [:th sl]
       [:th]
       [:th msh]
       [:th msm]
       [:th msl]]
      [:tr
       [:th {:col-span 8}
        [:& render-count]]]]]))

(defn mount! []
  (mf/mount (mf/element binary-clock) (dom/getElement "binary-clock")))

