(ns rumext.util)

(defn collect
  [key mixins]
  (seq (into [] (keep (fn [m] (get m key))) mixins)))

(defn collect*
  [keys mixins]
  (let [xf (mapcat (fn [m] (keep (fn [k] (get m k)) keys)))]
    (seq (into [] xf mixins))))

(defn call-all
  ([state fns]
   (reduce #(%2 %1) state fns))
  ([state fns & args]
   (reduce #(apply %2 %1 args) state fns)))

(defn obj->map
  "Convert shallowly an js object to cljs map."
  [obj]
  (let [keys (.keys js/Object obj)
        len (alength keys)]
    (loop [i 0
           r (transient {})]
      (if (< i len)
        (let [key (aget keys i)]
          (recur (unchecked-inc i)
                 (assoc! r (keyword key) (unchecked-get obj key))))
        (persistent! r)))))

(defn map->obj
  [o]
  (let [m #js {}]
    (run! (fn [[k v]] (unchecked-set m (name k) v)) o)
    m))

(defn wrap-props
  [props]
  (cond
    (map? props) props
    (nil? props) {}
    (object? props) (obj->map props)
    :else (throw (ex-info "Unecpected props" {:props props}))))

