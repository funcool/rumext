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
    :else (throw (ex-info "Unexpected props" {:props props}))))

(defn props-equals?
  [eq? new-props old-props]
  (let [old-keys (.keys js/Object old-props)
        new-keys (.keys js/Object new-props)
        old-keys-len (alength old-keys)
        new-keys-len (alength new-keys)]
    (if (identical? old-keys-len new-keys-len)
      (loop [idx (int 0) ret true]
        (if (< idx new-keys-len)
          (let [key (aget new-keys idx)
                new-val (unchecked-get new-props key)
                old-val (unchecked-get old-props key)
                result (eq? new-val old-val)]
            (if ^boolean result
              (recur (inc idx) result)
              false))
          ret))
      false)))
