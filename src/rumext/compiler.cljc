(ns rumext.compiler
  "
  Hicada - Hiccup compiler aus dem Allgaeu

  NOTE: The code for has been forked like this:
  weavejester/hiccup -> r0man/sablono -> Hicada -> rumext"
  (:refer-clojure :exclude [compile])
  (:require
   [rumext.normalize :as norm]
   [rumext.util :as util]))

(def ^:dynamic *handlers* nil)

(def default-handlers
  {:> (fn [_ klass attrs & children]
        [klass attrs children])
   :& (fn
        ([_ klass]
         (let [klass klass]
           [klass {} nil]))
        ([_ klass props & children]
         (let [klass klass]
           (if (map? props)
             [klass (rumext.util/compile-map->object props) children]
             [klass (list 'rumext.util/map->obj props) children]))))
   :* (fn [_ attrs & children]
        (if (map? attrs)
          ['rumext.alpha/Fragment attrs children]
          ['rumext.alpha/Fragment {} (cons attrs children)]))})

(declare emit-react)

(defn- compile-class-attr
  [value]
  (cond
    (or (nil? value)
        (keyword? value)
        (string? value))
    value

    (and (or (sequential? value)
             (set? value))
         (every? string? value))
    (util/join-classes value)

    (vector? value)
    (apply util/compile-join-classes value)

    :else value))

(defn compile-attr
  [[key val :as kvpair]]
  (cond
    (= key :class)       [:className (compile-class-attr val)]
    (= key :style)       [key (util/camel-case-keys val)]
    (= key :for)         [:htmlFor val]
    (or (keyword? key)
        (symbol? key))   [(util/camel-case key) val]
    :else                kvpair))

(declare compile*)

(defmulti compile-form
  "Pre-compile certain standard forms, where possible."
  (fn [form]
    (when (and (seq? form) (symbol? (first form)))
      (name (first form)))))

(defmethod compile-form "do"
  [[_ & forms]]
  `(do ~@(butlast forms) ~(compile* (last forms))))

(defmethod compile-form "array"
  [[_ & forms]]
  `(cljs.core/array ~@(mapv compile* forms)))

(defmethod compile-form "let"
  [[_ bindings & body]]
  `(let ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "let*"
  [[_ bindings & body]]
  `(let* ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "letfn*"
  [[_ bindings & body]]
  `(letfn* ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "for"
  [[_ bindings body]]
  ;; Special optimization: For a simple (for [x xs] ...) we rewrite the for
  ;; to a fast reduce outputting a JS array:
  (if (== 2 (count bindings))
    (let [[item coll] bindings]
      `(reduce (fn [out-arr# ~item]
                 (.push out-arr# ~(compile* body))
                 out-arr#)
               (cljs.core/array) ~coll))
    ;; Still optimize a little by giving React an array:
    (list 'cljs.core/into-array `(for ~bindings ~(compile* body)))))

(defmethod compile-form "if"
  [[_ condition & body]]
  `(if ~condition ~@(doall (for [x body] (compile* x)))))

(defmethod compile-form "when"
  [[_ bindings & body]]
  `(when ~bindings ~@(doall (for [x body] (compile* x)))))

(defmethod compile-form "when-some"
  [[_ bindings & body]]
  `(when-some ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "when-let"
  [[_ bindings & body]]
  `(when-let ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "when-first"
  [[_ bindings & body]]
  `(when-first ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "when-not"
  [[_ bindings & body]]
  `(when-not ~bindings ~@(doall (for [x body] (compile* x)))))

(defmethod compile-form "if-not"
  [[_ bindings & body]]
  `(if-not ~bindings ~@(doall (for [x body] (compile* x)))))

(defmethod compile-form "if-some"
  [[_ bindings & body]]
  `(if-some ~bindings ~@(doall (for [x body] (compile* x)))))

(defmethod compile-form "if-let"
  [[_ bindings & body]]
  `(if-let ~bindings ~@(doall (for [x body] (compile* x)))))

(defmethod compile-form "letfn"
  [[_ bindings & body]]
  `(letfn ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "fn"
  [[_ params & body]]
  `(fn ~params ~@(butlast body) ~(compile* (last body))))

(defmethod compile-form "case"
  [[_ v & cases]]
  `(case ~v
     ~@(doall (mapcat
                (fn [[test hiccup]]
                  (if hiccup
                    [test (compile* hiccup)]
                    [(compile* test)]))
                (partition-all 2 cases)))))

(defmethod compile-form "condp"
  [[_ f v & cases]]
  `(condp ~f ~v
     ~@(doall (mapcat
                (fn [[test hiccup]]
                  (if hiccup
                    [test (compile* hiccup)]
                    [(compile* test)]))
                (partition-all 2 cases)))))

(defmethod compile-form "cond"
  [[_ & clauses]]
  `(cond ~@(doall
            (mapcat
             (fn [[check expr]] [check (compile* expr)])
             (partition 2 clauses)))))

(defmethod compile-form :default [expr] expr)

(defn compile-element
  "Returns an unevaluated form that will render the supplied vector as a HTML element."
  [[tag attrs & children :as element]]
  (cond
    ;; e.g. [:> Component {:key "xyz", :foo "bar} ch0 ch1]
    (contains? *handlers* tag)
    (let [f (get *handlers* tag)
          [klass attrs children] (apply f element)]
      (emit-react klass attrs (mapv compile* children)))

    ;; e.g. [:span {} x]
    (and (util/literal? tag) (map? attrs))
    (let [[tag attrs _] (norm/element [tag attrs])]
      (emit-react tag attrs (mapv compile* children)))

    (util/literal? tag)
    ;; We could now interpet this as either:
    ;; 1. First argument is the attributes (in #js{} provided by the user) OR:
    ;; 2. First argument is the first child element.
    ;; We assume #2. Always!
    (compile-element (list* tag {} attrs children))

    ;; Problem: [a b c] could be interpreted as:
    ;; 1. The coll of ReactNodes [a b c] OR
    ;; 2. a is a React Element, b are the props and c is the first child
    ;; We default to 1) (handled below) BUT, if b is a map, we know this must be 2)
    ;; since a map doesn't make any sense as a ReactNode.
    ;; [foo {...} ch0 ch1] NEVER makes sense to interpret as a sequence
    (and (vector? element) (map? attrs))
    (emit-react tag attrs (mapv compile* children))

    (seq? element)
    (seq (mapv compile* element))

    ;; We have nested children
    ;; [[:div "foo"] [:span "foo"]]
    :else
    (mapv compile* element)))

(defn compile*
  "Pre-compile data structures"
  [content]
  (cond
    (vector? content)       (compile-element content)
    (util/literal? content) content
    :else                   (compile-form content)))

(defn tag->el
  [x]
  (assert (or (symbol? x) (keyword? x) (string? x) (seq? x))
          (str "Got: " (#?(:clj class :cljs type) x)))
  (if (keyword? x)
    (name x)
    x))

(def props-xform
  (comp
   (remove (fn [[k v]] (= k :key)))
   (map compile-attr)))

(defn emit-react
  "Emits the final react js code"
  [tag attrs children]
  (let [tag         (tag->el tag)
        children    (into [] (filter some?) children)
        [key props] (if (map? attrs)
                      [(or (:key attrs)
                           'rumext.alpha/undefined)
                       (->> (into {} props-xform attrs)
                            (util/compile-to-js))]
                       ['rumext.alpha/undefined attrs])]
    (cond
      (= 0 (count children))
      (list 'rumext.alpha/jsx tag props key)

      (= 1 (count children))
      (list 'rumext.alpha/jsx tag props key (first children))

      :else
      (list 'rumext.alpha/jsxs tag props key (apply list 'cljs.core/array children)))))

(defn compile
  "Arguments:
  - content: The hiccup to compile
  - handlers: A map to handle special tags. See default-handlers in this namespace.
  "
  ([content]
   (compile content nil))
  ([content handlers]
   (binding [*handlers* (merge default-handlers handlers)]
     (compile* content))))
