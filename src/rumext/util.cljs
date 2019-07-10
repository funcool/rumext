(ns rumext.util)

(defn collect
  [key mixins]
  (into [] (keep (fn [m] (get m key))) mixins))

(defn collect*
  [keys mixins]
  (let [xf (mapcat (fn [m] (keep (fn [k] (get m k)) keys)))]
    (into [] xf mixins)))

(defn call-all
  ([state fns]
   (reduce #(%2 %1) state fns))
  ([state fns & args]
   (reduce #(apply %2 %1 args) state fns)))
