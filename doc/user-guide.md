# User Guide

Simple and decomplexed UI library based on React >= 18 focused on performance.

Add to `deps.edn`:

```clojure
funcool/rumext
{:git/tag "v2.16"
 :git/sha "74e13d2"
 :git/url "https://github.com/funcool/rumext.git"}
```

## First Steps

Function components, as their name says, are defined using plain
functions. Rumext exposes a lightweight macro over a `fn` that adds
some additional facilities.

Let's see an example of how to define a component:

```clojure
(require '[rumext.v2 :as mf])

(mf/defc title*
  [{:keys [name] :as props}]
  [:div {:class "label"} name])
```

The received props are just plain JavaScript objects, so instead of
destructuring, you can access props directly using an imperative
approach (just a demonstrative example):

```clojure
(mf/defc title*
  [props]
  (let [name (unchecked-get props "name")]
    [:div {:class "label"} name]))
```

And finally, we mount the component onto the DOM:

```clojure
(ns myname.space
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]))

(def root (mf/create-root (dom/getElement "app")))
(mf/render! root (mf/element title* #js {:title "hello world"}))
```

**NOTE**: Important: the `*` in the name is a mandatory convention for
proper visual distinction of React components and Clojure functions.

**NOTE**: It also enables the current defaults on how props are
handled. If you don't use the `*` suffix, the component will behave in
legacy mode.

## Props & Destructuring

The destructuring works very similar to the Clojure map destructuring
with small differences and convenient enhancements for making working
with React props and idioms easy.

```clojure
(mf/defc title*
  [{:keys [name] :as props}]
  (assert (object? props) "expected object")
  (assert (string? name) "expected string")
  (assert (= (unchecked-get props "name")
             name)
          "expected string")

  [:label {:class "label"} name])
```

An additional idiom (specific to the Rumext component macro and not
available in standard Clojure destructuring) is the ability to obtain
an object with all non-destructured props:

```clojure
(mf/defc title*
  [{:keys [name] :rest props}]
  (assert (object? props) "expected object")
  (assert (nil? (unchecked-get props "name")) "no name in props")

  ;; The `:>` will be explained later
  [:> :label props name])
```

This allows you to extract the props that the component has control of
and leave the rest in an object that can be passed as-is to the next
element.

## JSX / Hiccup

You may already be familiar with Hiccup syntax (which is equivalent to
the React JSX) for defining the React DOM. The intention of this
section is to explain only the essential part of it and the
peculiarities of Rumext.

Let's start with simple generic elements like `div`:

```clojure
[:div {:class "foobar"
       :style {:background-color "red"}
       :on-click some-on-click-fn}
  "Hello World"]
```

The props and the style are transformed at compile time into a JS
object, transforming all keys from lisp-case to camelCase (and
renaming `:class` to `className`); so the compilation results in
something like this:

```js
const h = React.createElement;

h("div", {className: "foobar",
          style: {"backgroundColor": "red"},
          onClick: someFn},
          "Hello World");
```

It should be noted that this transformation is only done to properties
that are keyword types and that properties that begin with `data-` and
`aria-` are left as-is without transforming, just like string
keys. The properties can be passed directly using camelCase syntax (as
React natively expects) if you want.

There are times when we'll need the element name to be chosen
dynamically or constructed at runtime; the props to be built
dynamically or created as an element from a user-defined component.

For this purpose, Rumext exposes a special handler: `:>`, a
general-purpose handler for passing dynamically defined props to DOM
native elements or creating elements from user-defined components.

Let's start with an example of how the element name can be defined dynamically:

```clojure
(let [element (if something "div" "span")]
  [:> element {:class "foobar"
               :style {:background-color "red"}
               :on-click some-on-click-fn}
    "Hello World"])
```

The props also can be defined dynamically:

```clojure
(let [props #js {:className "fooBar"
                 :style #js {:backgroundColor "red"}
                 :onClick some-on-click}]
  [:> "div" props "Hello World"])
```

Remember, if props are defined dynamically, they should be defined as
plain JS objects respecting the React convention for props casing
(this means we need to pass `className` instead of `:class` for
example).

In the same way, you can create an element from a user-defined component:

```clojure
(mf/defc my-label*
  [{:keys [name class on-click] :rest props}]
  (let [class (or class "my-label")
        props (mf/spread props {:class class})]
    [:span {:on-click on-click}
      [:> :label props name]]))

(mf/defc other-component
  []
  [:> my-label* {:name "foobar" :on-click some-fn}])
```

As you can observe, in destructuring, we use a Clojure convention for
naming and casing of properties; it is the Rumext `defc` macro
responsible for the automatic handling of all naming and casing
transformations at compile time (for example: the `on-click` will
match the `onClick` on received props, and `class` will match the
`className` prop).

The `mf/spread` macro allows merging one or more JS object props
into one, always respecting the casing and naming conventions of React
for the props. Read more about it below.

## Props Checking

The Rumext library comes with two approaches for checking props:
**simple** and **malli**.

Let's start with the **simple**, which consists of simple existence
checks or plain predicate checking:

```clojure
(mf/defc button*
  {::mf/expect #{:name :on-click}}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

The prop names obey the same rules as the destructuring so you should
use the same names in destructuring. Sometimes a simple existence
check is not enough; for those cases, you can pass a map where keys
are props and values are predicates:

```clojure
(mf/defc button*
  {::mf/expect {:name string?
                :on-click fn?}}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

If that is not enough, it also supports
**[malli](https://github.com/metosin/malli)** as a validation
mechanism for props:

```clojure
(def ^:private schema:props
  [:map {:title "button:props"}
   [:name string?]
   [:class {:optional true} string?]
   [:on-click fn?]])

(mf/defc button
  {::mf/props :obj
   ::mf/schema schema:props}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

**NOTE**: The props checking obeys the `:elide-asserts` compiler
option and by default, they will be removed in production builds if
the configuration value is not changed explicitly.

## Higher-Order Components

This is the way you extend/add additional functionality to a function
component. Rumext exposes one:

- `mf/memo`: analogous to `React.memo`, adds memoization to the
  component based on props comparison.
- `mf/memo'`: identical to the `React.memo`

To use the higher-order components, you need to wrap the component
manually or pass it as a special property in the metadata:

```clojure
(mf/defc title*
  {::mf/wrap [mf/memo]}
  [{:keys [name]}]
  [:div {:class "label"} name])
```

By default, the `identical?` predicate is used to compare props; you
can pass a custom comparator function as a second argument:

```clojure
(mf/defc title*
  {::mf/wrap [#(mf/memo % =)]}
  [{:keys [name]}]
  [:div {:class "label"} name])
```

For convenience, Rumext has a special metadata `::mf/memo` that
facilitates the general case for component props memoization. If you
pass `true`, it will behave the same way as `::mf/wrap [mf/memo]` or
`React.memo(Component)`. You also can pass a set of fields; in this
case, it will create a specific function for testing the equality of
that set of props.

If you want to create your own higher-order component, you can use the
`mf/fnc` macro:

```clojure
(defn some-factory
  [component param]
  (mf/fnc my-high-order-component*
    [props]
    [:section
     [:> component props]]))
```

## Hooks

The Rumext library exposes a few specific hooks and some wrappers over
existing React hooks in addition to the hooks that React offers
itself.

You can use both one and the other interchangeably, depending on which
type of API you feel most comfortable with. The React hooks are
exposed as they are in React, with the function name in camelCase, and
the Rumext hooks use the lisp-case syntax.

Only a subset of available hooks is documented here; please refer to
the API reference documentation for detailed information about
available hooks.

### use-state

This is analogous to the `React.useState`. It exposes the same
functionality but uses the ClojureScript atom interface.

Calling `mf/use-state` returns an atom-like object that will deref to
the current value, and you can call `swap!` and `reset!` on it to
modify its state. The returned object always has a stable reference
(no changes between rerenders).

Any mutation will schedule the component to be rerendered.

```clojure
(require '[rumext.v2 as mf])

(mf/defc local-state*
  [props]
  (let [clicks (mf/use-state 0)]
    [:div {:on-click #(swap! clicks inc)}
      [:span "Clicks: " @clicks]]))
```

Alternatively, you can use the React hook directly:

```clojure
(mf/defc local-state*
  [props]
  (let [[counter update-counter] (mf/useState 0)]
    [:div {:on-click (partial update-counter #(inc %))}
      [:span "Clicks: " counter]]))
```

### use-var

In the same way as `use-state` returns an atom-like object. The unique
difference is that updating the ref value does not schedule the
component to rerender. Under the hood, it uses the `useRef` hook.

### use-effect

Analogous to the `React.useEffect` hook with a minimal call convention
change (the order of arguments is inverted).

This is a primitive that allows incorporating probably effectful code
into a functional component:

```clojure
(mf/defc local-timer*
  [props]
  (let [local (mf/use-state 0)]
    (mf/use-effect
      (fn []
        (let [sem (js/setInterval #(swap! local inc) 1000)]
          #(js/clearInterval sem))))
    [:div "Counter: " @local]))
```

The `use-effect` is a two-arity function. If you pass a single
callback function, it acts as though there are no dependencies, so the
callback will be executed once per component (analogous to `didMount`
and `willUnmount`).

If you want to pass dependencies, you have two ways:

- passing a JS array
- using the `rumext.v2/deps` helper

```clojure
(mf/use-effect
  (mf/deps x y)
  (fn [] (do-stuff x y)))
```

And finally, if you want to execute it on each render, pass `nil` as
deps (much in the same way as raw `useEffect` works).

For convenience, there is an `mf/with-effect` macro that drops one
level of indentation:

```clojure
(mf/defc local-timer*
  [props]
  (let [local (mf/use-state 0)]
    (mf/with-effect []
      (let [sem (js/setInterval #(swap! local inc) 1000)]
        #(js/clearInterval sem)))
    [:div "Counter: " @local]))
```

Here, the deps must be passed as elements within the vector (the first
argument).

Obviously, you can use the React hook directly via `mf/useEffect`.

### use-memo

In the same line as the `use-effect`, this hook is analogous to the
React `useMemo` hook with the order of arguments inverted.

The purpose of this hook is to return a memoized value.

Example:

```clojure
(mf/defc sample-component*
  [{:keys [x]}]
  (let [v (mf/use-memo (mf/deps x) #(pow x 10))]
    [:span "Value is: " v]))
```

On each render, while `x` has the same value, the `v` only will be
calculated once.

This also can be expressed with the `rumext.v2/with-memo` macro that
removes a level of indentation:

```clojure
(mf/defc sample-component*
  [{:keys [x]}]
  (let [v (mf/with-memo [x]
            (pow x 10))]
    [:span "Value is: " v]))
```

### use-fn

Is a special case of `use-memo`. An alias for `use-callback`.

### deref

A Rumext custom hook that adds reactivity to atom changes to the
component:

Example:

```clojure
(def clock (atom (.getTime (js/Date.))))
(js/setInterval #(reset! clock (.getTime (js/Date.))) 160)

(mf/defc timer*
  [props]
  (let [ts (mf/deref clock)]
    [:div "Timer (deref): "
     [:span ts]]))
```

Internally, it uses the `react.useSyncExternalStore` API together with
the ability of atom to watch it.

## Helpers

### Working with props

The Rumext library comes with a small set of helpers that facilitate
working with props JS objects. Let's look at them:

#### `mf/spread`

A **macro** that allows performing a merge between two props data
structures using the JS spread operator. Always preserving the React
props casing and naming convention.

It is commonly used this way:

```clojure
(mf/defc my-label*
  [{:keys [name class on-click] :rest props}]
  (let [class (or class "my-label")
        props (mf/spread props {:class class})]
    [:span {:on-click on-click}
      [:> :label props name]]))
```

The second argument should be a map literal in order to make the case
and naming transformation work correctly. If you pass there a symbol,
it should already be a correctly created props object.

#### `mf/props`

A helper **macro** that allows defining props objects from a map
literal.

An example of how it can be used and combined with `mf/spread`:

```clojure
(mf/defc my-label*
  [{:keys [name class on-click] :rest props}]
  (let [class (or class "my-label")
        new-props (mf/props {:class class})
        all-props (mf/spread props new-props)]
    [:span {:on-click on-click}
      [:> :label props name]]))
```

If you pass a symbol literal (props defined elsewhere using a Clojure
data structure) to the `mf/props` macro, a dynamic transformation will
be emitted for this expression.

```clojure
(let [clj-props {:class "my-label"}
      props (mf/props clj-props)]
  [:> :label props name])
```


In this example, `props` binding will contain a plain props js object
converted dinamically from clojure map at runtime. This should be
avoided if performance is important because it adds the overhead of
dynamic conversion on each render.

## FAQ

### Differences with RUM

This project is originated as a friendly fork of
[rum](https://github.com/tonsky/rum) for a personal use but it is
evolved to be a completly independent library that right now does not
depend on it and probably no longer preserves any of the original
code. In any case, many thanks to Tonksy for creating rum.

This is the list of the main differences:

- use function based components instead of class based components.
- a clojurescript friendly abstractions for React Hooks.
- the component body is compiled statically (never interprets at
  runtime thanks to **hicada**).
- performance focused, with a goal to offer almost 0 runtime
  overhead on top of React.

**WARNING**: it is mainly implemented to be used in
[penpot](https://github.com/penpot/penpot) and released as separated
project for conveniendce. Don't expect compromise for backward
compatibility beyond what the penpot project needs.


### What is the legacy mode?

Components that name does not use `*` as a suffix behaves in legacy
mode. It means parameter will be received as clojure map without any
transformation (with some exceptions for `className`).

That components should use `:&` handler when creating JSX/Hiccup
elements.

It is present for backward compatibility and should not be used.

## License

Licensed under MPL-2.0 (see LICENSE file on the root of the repository)
