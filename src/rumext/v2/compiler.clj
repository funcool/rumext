;; TODO: move to .CLJ file

(ns rumext.v2.compiler
  "
  Hicada - Hiccup compiler aus dem Allgaeu

  NOTE: The code for has been forked like this:
  weavejester/hiccup -> r0man/sablono -> Hicada -> rumext"
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.core :as c]
   [clojure.string :as str]
   [rumext.v2.normalize :as norm])
  (:import
   cljs.tagged_literals.JSValue))

(declare ^:private compile-to-js)
(declare ^:private emit-react)
(declare ^:private compile*)
(declare ^:private js-value?)

(def ^:dynamic *handlers* nil)

(def default-handlers
  {:> (fn [_ klass props & children]
        [klass props children])
   :& (fn
        ([_ klass]
         (let [klass klass]
           [klass {} nil]))
        ([_ klass props & children]
         (let [klass klass]
           (cond
             (map? props)      [klass (compile-to-js props) children]
             (symbol? props)   [klass (list 'rumext.v2.util/map->obj props) children]
             (js-value? props) [klass props children]
             :else             [klass props children]))))

   :? (fn [_ props & children]
        ['rumext.v2/Suspense props children])

   :* (fn [_ props & children]
        (if (or (map? props)
                (symbol? props)
                (js-value? props))
          ['rumext.v2/Fragment props children]
          ['rumext.v2/Fragment {} (cons props children)]))})

(defn- js-value?
  [o]
  (instance? JSValue o))

(defn- unevaluated?
  "True if the expression has not been evaluated.
   - expr is a symbol? OR
   - it's something like (foo bar)"
  [expr]
  (or (symbol? expr)
      (and (seq? expr)
           (not= (first expr) `quote))))

(defn- literal?
  "True if x is a literal value that can be rendered as-is."
  [x]
  (and (not (unevaluated? x))
       (or (not (or (vector? x) (map? x)))
           (and (every? literal? x)
                (not (keyword? (first x)))))))

(defn- join-classes
  "Join the `classes` with a whitespace."
  [classes]
  (->> (map #(if (string? %) % (seq %)) classes)
       (flatten)
       (remove nil?)
       (str/join " ")))

(defn- compile-concat
  [params & {:keys [safe?]}]
  (let [xform  (comp (filter some?)
                     (if safe?
                       (map (fn [part]
                              (if (string? part)
                                part
                                (list 'js* "(~{} ?? \"\")" part))))
                       (map identity)))
        params (into [] xform params)]

    (if (= 1 (count params))
      (first params)
      (let [templ (->> (repeat (count params) "~{}")
                       (interpose "+")
                       (reduce c/str ""))]
        (apply list 'js* templ params)))))

(defn- compile-join-classes
  "Joins strings space separated"
  ([] "")
  ([& xs] (compile-concat (interpose " " xs) :safe? true)))

(defn- compile-class-attr-value
  [value]
  (cond
    (or (nil? value)
        (keyword? value)
        (string? value))
    value

    ;; If we know all classes at compile time, we just join them
    ;; correctly and return.
    (and (or (sequential? value)
             (set? value))
         (every? string? value))
    (join-classes value)

    ;; If we don't know all classes at compile time (some classes are
    ;; defined on let bindings per example), then we emit a efficient
    ;; concatenation code that executes on runtime
    (vector? value)
    (apply compile-join-classes value)

    :else value))


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

(defn- compile-element
  "Returns an unevaluated form that will render the supplied vector as a HTML element."
  [[tag props & children :as element]]
  (cond
    ;; e.g. [:> Component {:key "xyz", :foo "bar} ch0 ch1]
    (contains? *handlers* tag)
    (let [f (get *handlers* tag)
          [klass props children] (apply f element)]
      (emit-react klass props (mapv compile* children)))

    ;; e.g. [:span {} x]
    (and (literal? tag) (map? props))
    (let [[tag props _] (norm/element [tag props])]
      (emit-react tag props (mapv compile* children)))

    (literal? tag)
    ;; We could now interpet this as either:
    ;; 1. First argument is the attributes (in #js{} provided by the user) OR:
    ;; 2. First argument is the first child element.
    ;; We assume #2. Always!
    (compile-element (list* tag {} props children))

    ;; Problem: [a b c] could be interpreted as:
    ;; 1. The coll of ReactNodes [a b c] OR
    ;; 2. a is a React Element, b are the props and c is the first child
    ;; We default to 1) (handled below) BUT, if b is a map, we know this must be 2)
    ;; since a map doesn't make any sense as a ReactNode.
    ;; [foo {...} ch0 ch1] NEVER makes sense to interpret as a sequence
    (and (vector? element) (map? props))
    (emit-react tag props (mapv compile* children))

    (seq? element)
    (seq (mapv compile* element))

    ;; We have nested children
    ;; [[:div "foo"] [:span "foo"]]
    :else
    (mapv compile* element)))

(defn- compile*
  "Pre-compile data structures"
  [content]
  (cond
    (vector? content)       (compile-element content)
    (literal? content) content
    :else                   (compile-form content)))

(defn- tag->el
  [x]
  (assert (or (symbol? x) (keyword? x) (string? x) (seq? x))
          (str "Got: " (class x)))
  (if (keyword? x)
    (name x)
    x))

(defn camel-case
  "Returns camel case version of the key, e.g. :http-equiv
  becomes :httpEquiv with some exceptions for specific type of keys"
  [k]
  (if (or (keyword? k) (symbol? k))
    (let [nword (name k)]
      (cond
        (str/starts-with? nword "--") nword
        (str/starts-with? nword "data-") nword
        (str/starts-with? nword "aria-") nword
        :else
        (let [[first-word & words] (str/split nword #"-")]
          (if (empty? words)
            nword
            (-> (map str/capitalize words)
                (conj first-word)
                str/join)))))
      k))

(defn- camel-case-keys
  "Recursively transforms all map keys into camel case."
  [m]
  (cond
    (map? m)
    (reduce-kv
      (fn [m k v]
        (assoc m (camel-case k) v))
      {} m)
    ;; React native accepts :style [{:foo-bar ..} other-styles] so camcase those keys:
    (vector? m)
    (mapv camel-case-keys m)
    :else
    m))

(defn compile-prop
  [[key val :as kvpair]]
  (cond
    (= key :class)       [:className (compile-class-attr-value val)]
    (= key :style)       [key (camel-case-keys val)]
    (= key :for)         [:htmlFor val]
    (or (keyword? key)
        (symbol? key))   [(camel-case key) val]
    :else                kvpair))

(defn compile-kv-to-js
  "A internal method helper for compile kv data structures"
  [form]
  (let [valid-key? #(or (keyword? %) (string? %))
        form       (into {} (filter (comp valid-key? key)) form)]


    [(->> form
          (map key)
          (map #(-> (str \' % "':~{}")))
          (interpose ",")
          (apply str))
     (mapv compile-to-js (vals form))]))

(defn compile-to-js
  "Compile a statically known data sturcture, recursivelly to js
  expression. Mainly used by macros for create js data structures at
  compile time."
  [form]
  (cond
    (map? form)
    (if (empty? form)
      (list 'js* "{}")
      (let [[keys vals] (compile-kv-to-js form)]
        (vary-meta
         (list* 'js* (str "{" keys "}") vals)
         assoc :tag 'object)))

    (vector? form)
    (apply list 'cljs.core/array (map compile-to-js form))

    (keyword? form)
    (name form)

    :else
    form))

(defn compile-to-spread-js-obj
  [target other]
  (cond
    (and (symbol? target)
         (symbol? other))
    (list 'js* "{...~{}, ...~{}}" target other)

    (and (symbol? target)
         (map? other))
    (let [[keys vals] (compile-kv-to-js other)
          template    (str "{...~{}, " keys "}")]
      (apply list 'js* template target vals))

    (and (map? target)
         (symbol? other))
    (let [[keys vals] (compile-kv-to-js target)
          template    (str "{" keys ", ...~{}}")]
      (apply list 'js* template (concat vals [other])))

    (and (map? target)
         (map? other))
    (compile-to-js (merge target other))

    :else
    (throw (IllegalArgumentException. "invalid arguments, only symbols or maps allowed"))))

(defn emit-react
  "Emits the final react js code"
  [tag props children]
  (let [tag      (tag->el tag)
        children (into [] (filter some?) children)]
    (if (map? props)
      (let [props (cond
                    (= 0 (count children))
                    props

                    (= 1 (count children))
                    (assoc props :children (peek children))

                    :else
                    (assoc props :children (apply list 'cljs.core/array children)))

            props (->> (into {} (map compile-prop) props)
                       (compile-to-js))]
        (if (> (count children) 1)
          (list 'rumext.v2/jsxs tag props)
          (list 'rumext.v2/jsx tag props)))

      (cond
        (= 0 (count children))
        (list 'rumext.v2/jsx tag props)

        (= 1 (count children))
        (list 'rumext.v2/jsx tag (list 'js* "{...~{}, children: ~{}}" props (first children)))

        :else
        (list 'rumext.v2/jsxs tag (list 'js* "{...~{}, children: ~{}}" props (apply list 'cljs.core/array children)))))))

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
