# User Guide

Simple and Decomplected UI library based on React >= 18 focused on
performance.

Add to deps.edn:

```clojure
funcool/rumext
{:git/tag "v2.9.3"
 :git/sha "9047337"
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
`::mf/props :native` for completly avoid props wrapping overhead.

```clojure
(mf/defc title
  {::mf/props :native}
  [props]
  (let [name (unchecked-get props "name")]
    [:div {:class "label"} name]))
```

NOTE: This should be the default, but because we have too many
components defined supposing the old behavior (where `props` is a
clojure map), we can't put that default right now. But in the future
it is possible that this will be the default.

In any case, having the props as a javascript object does not make
destructuring unusable. The `defc` macro allows basic use of
destructuring even if `props` is a plain javascript object:

```clojure
(mf/defc title
  {::mf/props :native}
  [{:keys [name] :as props}]
  [:div {:class "label"} name])
```

Here, the `props` is a plain js object but we destructure correctly
the `name` prop.


And finally, we mount the component on the dom:

```clojure
(ns myname.space
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]))

(def root (mf/create-root (dom/getElement "app")))
(mf/render! root (mf/element title #js {:title "hello wolrd"}))
```


## JSX & Call Conventions

You may be already familiar with hiccup syntax (which is equivalent to
the react JSX) for defining the react dom. The intention on this
section is explain only the essential part of it and the peculiarities
of rumext.

### Native elements

Lets start with simple generic components like `:div`:

```clojure
[:div {:class "foobar"
       :style {:background-color "red"}
       :on-click some-on-click-fn}
  "Hello World"]
```

As you can observe, looks very familiar. The props and the style are
transformed at compile time to a js object transforming all keys from
lisp-case to camelCase (and rename `:class` to `className`); so the
result will look aproximatelly like this in jsx:

```javascript
const h = React.createElement;

h("div", {className: "foobar",
          style: {"backgroundColor": "red"},
          onClick=someFn},
          "Hello World");
```

It should be noted that this transformation is only done to properties
that are keyword type and that properties that begin with `data-` and
`aria-` are left as is without transforming just like the string keys.

Obviously the keyword properties can be passed directly using
camelCase syntax (as react nativelly expects) but for convenience the
rumext compiler converts lisp-case to camelCase for you (`on-click` ->
`onClick`).

There are times when we will need the element name to be chosen
dynamically or constructed in runtime. For these cases rumext offers
handlers. For this specific case the handler is `[:> ...]`.

Using the same example as before, the equivalent code would be:

```clojure
[:> "div" {:class "foobar"
           :style {:background-color "red"}
           :on-click some-on-click-fn}
  "Hello World"]
```

Since rumext uses compile-time transformations (macros) to transform
data structures from clojure to the react dom, all data must be
literals that the macro can understand. But there are times when we
need to be able to build the props dynamically and in this case we
have no choice but to build the props in a javascript object.

```clojure
(let [props #js {:className "fooBar"
                 :stye #js {:backgroundColor "red"}
                 :onClick some-on-click}]
  [:> "div" props "Hello World"])
```

### User defined components

Components are everything that we as users define.

In this case we have two ways to call our component (or in react
words, create the react-dom element from a user-defined
component):

**A**: The first is when we have 100% control of the props and we do
not want any type of transformation to be done to them (usually when
we are talking about large components, you probably do not reuse that
they represent a page or a section of that page).

**B**: And the second is when we are creating a reusable component
that is probably wrapping one or more native elements of the virtual
dom and we simply want to extend its behavior controlling only a
subset of props, where the rest of the props that are not controlled
would be passed as is. to the next native element.

For the **A** case, we will use the `[:& ...]` handler:

```clojure
(mf/defc title
  {::mf/props :native}
  [{:keys [name on-click]}]
  [:div {:class "label" :on-click on-click} name])

(mf/defc my-big-component
  []
  [:& title {:name "foobar" :on-click some-fn}])
```

You can see how the properties passed to the `title` component are
passed as is, without any kind of transformation in the props (for
example `on-click` is still passed as `on-click` using the lisp-case)


For the **B** case, we will use the already known `[:> ...]` handler:

```clojure
(mf/defc button
  {::mf/props :native}
  [{:keys [name onClick]}]
  [:button {:on-click on-click} name])

(mf/defc my-big-component
  []
  [:> button {:name "foobar" :on-click some-fn}])
```

You can see that when using the `[:>` handler we pass the props using
lisp-style syntax which are automatically transformed into camelCase,
and the component receives the parameters in a raw or native form from
react (that is, in camelCase).

Remember that `::mf/props :native` should probably be a default, so
all components you define should have that metadata.

For convenience, if the component is named with an `*` at the end of
the name or it has the `::mf/props-destructuring :lisp-to-camel` prop
in the metadata along with `::mf/props :native`, the destructuring can
use the lisp-case and the macro will automatically access the value
with camelCase from the props.

```
(mf/defc button*
  {::mf/props :native}
  [{:keys [name on-clic]}]
  [:button {:on-click on-click} name])

(mf/defc my-big-component
  []
  [:> button {:name "foobar" :onClick some-fn}])
```

The **B** case is also useful if the intention for the components is
to be reusable externally, in non-clojure code bases (for example in a
storybook).


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
   ::mf/props :native}
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
    {::mf/props :native}
    [props]
    [:section
     [:> component props]]))
```


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
