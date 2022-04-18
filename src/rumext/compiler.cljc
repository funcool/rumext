(ns rumext.compiler
  "
  Hicada - Hiccup compiler aus dem Allgaeu

  NOTE: The code for has been forked like this:
  weavejester/hiccup -> r0man/sablono -> Hicada -> rumext"
  (:refer-clojure :exclude [compile])
  (:require
   [rumext.normalize :as norm]
   [rumext.util :as util]))

(def ^:dynamic *config* default-config)
(def ^:dynamic *handlers* default-handlers)

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
             [klass (rumext.util/compile-to-js* props) children]
             [klass (list 'rumext.util/map->obj props) children]))))
   :* (fn [_ attrs & children]
        (if (map? attrs)
          ['rumext.alpha/Fragment attrs children]
          ['rumext.alpha/Fragment {} (cons attrs children)]))})

(def default-config
  {:array-children? true
   :emit-fn nil
   :rewrite-for? true
   :camelcase-key-pred (some-fn keyword? symbol?)
   :transform-fn identity
   :create-element 'rumext.alpha/create-element})

(declare emit-react)

;; (defn- compile-jsx-element
;;   "Render an element vector as a JSX/React element."
;;   [element]
;;   (let [[tag attrs content] (norm/element element)]
;;     (emit-react tag attrs (when content (compile-jsx-form content)))))

;; (defn- compile-jsx-form
;;   [form]
;;   (cond
;;     (and (vector? form)
;;          (util/element? form))
;;     (compile-jsx-element form)

;;     (or (vector? form)
;;         (seq? form))
;;     (mapv compile-jsx-form form)

;;     :else
;;     form))

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
    (apply util/join-classes-js value)

    :else value))

(defn compile-attr
  [[key val :as kvpair]]
  (let [to-camel-case? (:camelcase-key-pred *config*)]
    (cond
      (= key :class)       [:className (compile-class-attr val)]
      (= key :style)       [key (util/camel-case-keys val)]
      (to-camel-case? key) [(util/came-case key) val]
      :else                kvpair)))

(defn compile-attrs
  "Compile a JSX attributes map."
  [attrs]
  (cond->> attrs
    (map? attrs)
    (into {} (map compile-attr))))

(defn- form-name
  "Get the name of the supplied form."
  [form]
  (when (and (seq? form) (symbol? (first form)))
    (name (first form))))

(declare compile*)

(defmulti compile-form
  "Pre-compile certain standard forms, where possible."
  form-name)

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
  (if (:rewrite-for? *config*)
    (if (== 2 (count bindings))
      (let [[item coll] bindings]
        `(reduce (fn ~'rumext-for-reducer [out-arr# ~item]
                   (.push out-arr# ~(compile* body))
                   out-arr#)
                 (cljs.core/array) ~coll))
      ;; Still optimize a little by giving React an array:
      (list 'cljs.core/into-array `(for ~bindings ~(compile* body))))
    `(for ~bindings ~(compile* body))))

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

(defmethod hc/compile-form "letfn"
  [[_ bindings & body]]
  `(letfn ~bindings ~@(butlast body) ~(compile* (last body))))

(defmethod hc/compile-form "fn"
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

    ;; e.g. [:span "foo"]
    ;(every? literal? element)
    ;(compile-jsx-form-element element)

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

;; (defn- collapse-one
;;   "We can collapse children to a non-vector if there is only one."
;;   [xs]
;;   (cond-> xs
;;     (== 1 (count xs)) first))

(defn tag->el
  "A :div is translated to \"div\" and symbol 'ReactRouter stays."
  [x]
  (assert (or (symbol? x) (keyword? x) (string? x) (seq? x))
          (str "Got: " (#?(:clj class :cljs type) x)))
  (if (keyword? x)
    (if (:no-string-tags? *config*)
      (symbol (or (namespace x) (some-> (:default-ns *config*) name)) (name x))
      (name x))
    x))

(defn emit-react
  "Emits the final react js code"
  [tag attrs children]
  (let [{:keys [transform-fn emit-fn create-element array-children? server-render?]} *config*
        [tag attrs children] (transform-fn [tag attrs children])
        children (if (and array-children?
                          (not (empty? children))
                          (< 1 (count children)))
                   ;; In production:
                   ;; React.createElement will just copy all arguments into
                   ;; the children array. We can avoid this by just passing
                   ;; one argument and make it the array already. Faster.
                   ;; Though, in debug builds of react this will warn about "no keys".
                   [(apply list 'cljs.core/array children)]
                   children)
        el     (tag->el tag)
        attrs  (if server-render? attrs (util/compile-to-js (compile-attrs attrs)))]
    (if emit-fn
      (emit-fn el cfg children)
      (apply list create-element el cfg children))))

(defn compile
  "Arguments:
  - content: The hiccup to compile
  - opts
   o :array-children? - for product build of React only or you'll enojoy a lot of warnings :)
   o :create-element 'js/React.createElement - you can also use your own function here.
   o :wrap-input? - if inputs should be wrapped. Try without!
   o :rewrite-for? - rewrites simple (for [x xs] ...) into efficient reduce pushing into
                          a JS array.
   o :emit-fn - optinal: called with [type config-js child-or-children]
   o :camelcase-key-pred - defaults to (some-fn keyword? symbol?), ie. map keys that have
                           string keys, are NOT by default converted from kebab-case to camelCase!
   o :transform-fn - Called with [[tag attrs children]] before emitting, to get
                     transformed element as [tag attrs children]

   React Native special recommended options:
   o :no-string-tags? - Never output string tags (don't exits in RN)
   o :default-ns - Any unprefixed component will get prefixed with this ns.
  - handlers:
   A map to handle special tags. See default-handlers in this namespace.
  - env: The macro environment. Not used currently."
  ([content]
   (compile content default-config))
  ([content opts]
   (compile content opts default-handlers))
  ([content opts handlers]
   (compile content opts handlers nil))
  ([content opts handlers env]
   (binding [*config*   (merge default-config opts)
             *handlers* (merge default-handlers handlers)]
     (compile* content))))
