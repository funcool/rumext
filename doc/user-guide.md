# User Guide

Simple and Decomplected UI library based on React >= 18 focused on
performance.

Add to deps.edn:

```clojure
funcool/rumext
{:git/tag "v2.10"
 :git/sha "d96ea18"
 :git/url "https://github.com/funcool/rumext.git"}
```

## First Steps

Function components as it's name says, are defined using plain
functions. Rumext exposes a lighweigh macro over a `fn` that convert
props from js-object to cljs map (shallow) and exposes a facility for
docorate (wrap) with other higher-order components.

Let's see a example of how to define a component:

```clojure
(require '[rumext.v2 :as mf])

(mf/defc title
  [{:keys [name] :as props}]
  [:div {:class "label"} name])
```

For performance reasons, you most likely want the props to arrive as
is, as a javascript object. For this case, you should use the metadata
`::mf/props :obj` for completly avoid props wrapping overhead (see the
next section, where it goes into more depth on the topic).

```clojure
(mf/defc title
  {::mf/props :obj}
  [props]
  (let [name (unchecked-get props "name")]
    [:div {:class "label"} name]))
```

And finally, we mount the component on the dom:

```clojure
(ns myname.space
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]))

(def root (mf/create-root (dom/getElement "app")))
(mf/render! root (mf/element title #js {:title "hello wolrd"}))
```

## Props & Destructuring

There are two way to approach props and its destructuring. By default
(if not explicitly set by metadata) the props objects has the clojure
hash-map type, and follows the already known clojure approach for
destructuring.

```clojure
(mf/defc title
  [{:keys [name] :as props}]
  (assert (map? props) "expected map")
  (assert (string? name) "expected string")

  [:div {:class "label"} name])
```

Not passing any value for `::mf/props` is equivalent to passing
`::mf/props :clj`. So this code is equivalent:

```clojure
(mf/defc title
  {::mf/props :clj}
  [{:keys [name] :as props}]
  (assert (map? props) "expected map")
  (assert (string? name) "expected string")

  [:div {:class "label"} name])
```

That approach is very convenient because when you start prototyping,
the received props obeys the already known idioms, and all works in a
way like the component is a simple clojure function.

But, this approach has inconvenience of the need to transform from js
object to clojure hash-map on each render and this has performance
penalization. In the majority of cases this has no isues at all.

But in cases when performance is important, it is recommended to use
the `::mf/props :obj` which completly removes the transformation
overhead.

The component functions with `::mf/props :obj` also has support for
the already familiar destructuring idiom. Internally, this compiles to
code that directly accesses properties within the props object. The
only thing to keep in mind, whether you use destructuring or not, **is
that the props object is a flat js object and not a clojure
hash-map**.

```clojure
(mf/defc title
  {::mf/props :obj}
  [{:keys [name] :as props}]
  (assert (object? props) "expected object")
  (assert (string? name) "expected string")
  (assert (unchecked-get props "name") "expected string")

  [:label {:class "label"} name])
```

An additional idiom, only available in case of using destructuring
with props as a js object: is the possibility of obtaining an object
with all properties not destructured:

```clojure
(mf/defc title
  {::mf/props :obj}
  [{:keys [name] :as props :rest other}]
  (assert (object? props) "expected object")
  (assert (object? other) "expected map")
  (assert (nil? (unchecked-get other "name")) "no name in other")

  ;; The `:>` will be explained later
  [:> :label other name])
```

This allows you to extract the props that the component has control of
and leave the rest in an object that can be passed as is to the next
element.


## JSX / Hiccup

You may be already familiar with hiccup syntax (which is equivalent to
the react JSX) for defining the react dom. The intention on this
section is explain only the essential part of it and the peculiarities
of rumext.

### Introduction

Lets start with simple generic elements like `div`:

```clojure
[:div {:class "foobar"
       :style {:background-color "red"}
       :on-click some-on-click-fn}
  "Hello World"]
```

The props and the style are transformed at compile time to a js object
transforming all keys from lisp-case to camelCase (and rename `:class`
to `className`); so the compilation result to something like this:

```js
const h = React.createElement;

h("div", {className: "foobar",
          style: {"backgroundColor": "red"},
          onClick=someFn},
          "Hello World");
```

It should be noted that this transformation is only done to properties
that are keyword type and that properties that begin with `data-` and
`aria-` are left as is without transforming just like the string
keys. The properties can be passed directly using camelCase syntax (as
react nativelly expects) if you want.


### Handlers & Call Conventions

There are times when we'll need:

- the element name to be chosen dynamically or constructed in runtime;
- the props to be build dinamically
- create an element from user defined component

For this purpose, rumext exposes a special handlers, each one exposing
its own call convention: `:&` and `:>`.

Lets start with `:&` handler. We will use it when we have 100% control
of the props and we do not want any type of transformation to be done
to them (usually when we are talking about large components, you
probably do not reuse that they represent a page or a section of that
page, but not limited to). All props passed to the element will be
passed as-is, without any kind of transformations to the prop keys nor
values.

```clojure
(mf/defc title
  {::mf/props :obj}
  [{:keys [name on-click]}]
  [:div {:class "label" :on-click on-click} name])

(mf/defc my-big-component
  []
  [:& title {:name "foobar" :on-click some-fn}])
```

And for completeness, an example without destructuring:

```clojure
(mf/defc title
  {::mf/props :obj}
  [props]
  (let [name     (unchecked-get props "name")
        on-click (unchecked-get props "on-click")]
    [:div {:class "label" :on-click on-click} name]))

(mf/defc my-big-component
  []
  [:& title {:name "foobar" :on-click some-fn}])
```

This is probably the handler that you will use the most time.

Then, we also have the `:>` handler. We will use it when we have the
following situations:

- We want to decide the element name dynamicaly

```clojure
(let [element (if something "div" "span")]
  [:> element {:class "foobar"
               :style {:background-color "red"}
               :on-click some-on-click-fn}
    "Hello World"])
```

- We want to build and pass props dinamically to create a DOM native
  element (note that the props are always js plain objects and using
  react naming convention)

```clojure
(let [props #js {:className "fooBar"
                 :style #js {:backgroundColor "red"}
                 :onClick some-on-click}]
  [:> "div" props "Hello World"])
```

- we are creating a reusable component that is probably wrapping one
  or more native elements of the virtual dom and we simply want to
  extend its behavior controlling only a subset of props, where the
  rest of the props that are not controlled would be passed as is to
  the next native element.

```clojure
(mf/defc my-label
  {::mf/props :obj}
  [{:keys [name className] :rest props}]
  (let [class (or className "my-label")
        props (mf/spread props {:className class})]
    [:> :label props name]))

(mf/defc other-component
  []
  [:> my-label {:name "foobar" :on-click some-fn}])
```

So, all the the props passed to the `:>` handler on creating an
element from `my-label` component are transformed at compile-time to
an js object following react convention (camelcasing keys, etc.); This
greatly facilitates the task of passing the props to the next element
without performing any additional transformation.

For this last case and to make the work even easier but preserving the
same code style as the rest of the application, rumext offers a way
for the destructuring to also take into account the rules and
conventions of react for the props keys.

This is achieved with the metadata `{::mf/props :react}` or by putting
the suffix `*` in the component name. And then, the destructuring can
use the lisp-case and the macro will automatically access the value
with camelCase from the props, respecting the react convention.

```
(mf/defc my-label*
  {::mf/props :obj}
  [{:keys [name class] :rest props}]
  (let [class (or class "my-label")
        props (mf/spread-props props {:class class})]
    [:> :label props name]))
```

But remember: **the `*` only changes the behavior of
destructuring**. The call convention is determined by the used
handler: `[:&` or `[:>`.


## Props Checking

Rumext comes with basic props checking that allows basic existence
checking or with simple predicate checking. For simple existence
checking, just pass a set with prop names.

```clojure
(mf/defc button
  {::mf/props :obj
   ::mf/expect #{:name :on-click}}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

The prop names obeys the same rules as the destructuring so you should
use the same names in destructuring.

You also can add some predicates:

```clojure
(mf/defc button
  {::mf/props :obj
   ::mf/expect {:name string?
                :on-click fn?}}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

The props checking obey the `:elide-asserts` compiler option and they
are removed on production builds.


## Higher-Order Components

This is the way you have to extend/add additional functionality to a
function component. Rumext exposes one:

- `mf/memo`: analogous to `React.memo`, adds memoization to the
  component based on props comparison.
- `mf/memo'`: identical to the `React.memo`

In order to use the high-order components, you need wrap the component
manually or passing it as a special property in the metadata:

```clojure
(mf/defc title
  {::mf/wrap [mf/memo]
   ::mf/props :obj}
  [props]
  [:div {:class "label"} (:name props)])
```

By default `identical?` predicate is used for compare props; you can
pass a custom comparator function as second argument:

```clojure
(mf/defc title
  {::mf/wrap [#(mf/memo % =)]}
  [props]
  [:div {:class "label"} (:name props)])
```

If you want create a own high-order component you can use `mf/fnc` macro:

```clojure
(defn some-factory
  [component param]
  (mf/fnc myhighordercomponent
    {::mf/props :obj}
    [props]
    [:section
     [:> component props]]))
```

The wrap is a generic mechanism for higher-order components, so you
can create your own wrappers when you need somethig specific.


### Special case for `memo`

For convenience, rumext has a special metadata `::mf/memo` that
facilitates a bit the general case for component props memoization. If
you pass `true`, then it will behave the same way as `::mf/wrap
[mf/memo]` or `React.memo(Component)`. You also can pass a set of
fields, in this case it will create a specific function for testing
for equality of that specific set of props.


## Hooks

The rumext library exposes a few specific hooks and some wrappers over existing react
hooks in addition to the hooks that react offers itself.

You can use both one and the other interchangeably, depending on which type of API you
feel most comfortable with. The react hooks are exposed as is in react, with the function
name in camelCase and the rumext hooks use the lisp-case syntax.

Only a subset of available hooks is documented here, please refer to the API reference
documentation for deatailed information of available hooks.


### use-state

This is analogous hook to the `React.useState`. It exposes the same functionality but
using ClojureScript atom interface.

Calling `mf/use-state` returns an atom-like object that will deref to the current value
and you can call `swap!`  and `reset!`  on it for modify its state.

Any mutation will schedule the component to be rerendered.

```clojure
(require '[rumext.v2 as mf])

(mf/defc local-state
  [props]
  (let [local (mf/use-state 0)]
    [:div {:on-click #(swap! local inc)}
      [:span "Clicks: " @local]]))

```

Alternatively, you can use the react hook directly:

```clojure
(mf/defc local-state
  [props]
  (let [[counter update-conter] (mf/useState 0)]
    [:div {:on-click (partial update-conter #(inc %))}
      [:span "Clicks: " counter]]))
```

### use-var

In the same way as `use-state` returns an atom like object. The unique difference is that
updating the ref value does not schedules the component to rerender. Under the hood it
uses useRef hook.


### use-effect

Analgous to the `React.useEffect` hook with minimal call convention change (the order of
arguments inverted).

This is a primitive that allows incorporate probably efectful code into a functional
component:

```clojure
(mf/defc local-timer
  [props]
  (let [local (mf/use-state 0)]
    (mf/use-effect
      (fn []
        (let [sem (js/setInterval #(swap! local inc) 1000)]
          #(js/clearInterval sem))))
    [:div "Counter: " @local]))
```

The `use-effect` is a two arity function. If you pass a single callback function it acts
like there are no dependencies, so the callback will be executed once per component
(analgous to `didMount` and `willUnmount`).

If you want to pass dependencies you have two ways:

- passing an js array
- using `rumext.v2/deps` helper

```clojure
(mf/use-effect
  (mf/deps x y)
  (fn [] (do-stuff x y)))
```

And finally, if you want to execute it on each render, pass `nil` as
deps (much in the same way as raw useEffect works).

For convenience, there is a `mf/with-effect` macro that drops one level
of indentation:

```clojure
(mf/defc local-timer
  [props]
  (let [local (mf/use-state 0)]
    (mf/with-effect []
      (let [sem (js/setInterval #(swap! local inc) 1000)]
        #(js/clearInterval sem)))
    [:div "Counter: " @local]))
```

Here, the deps must be passed as elements within the vector (the first argument).

Obviously you can use the react hook directly via `mf/useEffect`.


### use-memo

In the same line as the `use-effect`, this hook is analogous to the react `useMemo` hook
with order of arguments inverted.

The purpose of this hook is return a memoized value.

Example:

```clojure
(mf/defc sample-component
  [{:keys [x]}]
  (let [v (mf/use-memo (mf/deps x) #(pow x 10))]
    [:span "Value is:" v]))
```

On each render, while `x` has the same value, the `v` only will be
calculated once.

This also can be expressed with the `rumext.v2/with-memo` macro that removes a level of
indentantion:

```clojure
(mf/defc sample-component
  [{:keys [x]}]
  (let [v (mf/with-memo [x]
            (pow x 10))]
    [:span "Value is:" v]))
```


### use-fn

Is a special case of `use-memo`. An alias for `use-callback`.


### deref

A rumext custom hook that adds ractivity to atom changes to the component:

Example:

```clojure
(def clock (atom (.getTime (js/Date.))))
(js/setInterval #(reset! clock (.getTime (js/Date.))) 160)

(mf/defc timer
  [props]
  (let [ts (mf/deref clock)]
    [:div "Timer (deref)" ": "
     [:span ts]]))
```

Internally it uses the `react.useSyncExternalStore` API together with the ability of atom
to watch it.


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
[penpot](https://github.com/penpot/penpot) and released as separated project for
conveniendce. Don't expect compromise for backward compatibility beyond what the penpot
project needs.


## License

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).
