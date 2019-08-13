# rumext #

This is a friendly fork of [rum](https://github.com/tonsky/rum).


## Using rumext

Add to deps.edn:

```
funcool/rumext {:mvn/version "2.0.0-SNAPSHOT"}
```

## Differences with rum

This is the list of the main differences that rumext introduces vs rum:

- clear distintion between class components and function components.
- function components uses React Hooks behind the scenes for provide
  **local state**, **pure components** and **reactivity** (the ability
  of rerender component on atom change).
- a clojurescript friendly abstractions for React Hooks.
- more idiomatic macro for define class based components, that allows
  include lifecycle methods directly (without need to create an ad-hoc
  mixin).
- the component body is compiled statically (never interprets at
  runtime thanks to **hicada**).
- the react class is build lazily (no react class is build until first
  call to the component). This allows reduce load time when you have a
  lot of components but on the "current page" only a few of them are
  used.
- don't use varargs on components (better performance)
- uses new react lifecyle methods (all deprecated lifecyles are
  removed).
- does not includes the cursors and derived-atoms; that stuff is
  delegated to third party libraries like
  [lentes](https://github.com/funcool/lentes).
- no server-side rendering


**WARNING**: this is not intended for general use, it is mainly
implemented to be used in [uxbox](https://github.com/uxbox/uxbox) and
released as separated project for conveniendce. Don't expect
compromise for backward compatibility.


## Class Components

### Defining a Class Component

Let's see an example of how to use rumext macros for define class
based components.

```clojure
(require '[rumext.alpha as mf])

(mf/def local-state
  :desc "A component docstring/description (optional)."
  :mixins [(mf/local 0)]
  :init
  (fn [own props]
    (println "Component initialized")
    own)

  :render
  (fn [own {:keys [title] :as props}]
    (let [*count (::mf/local state)]
      [:div {:on-click #(swap! *count inc)}
        [:span title ": " @*count]])))

(mf/def parent-component
  :render
  (fn [own props]
    [:section
     [:h1 "Some title"]
     [:& local-state {:title "Some title"}]]))
```

This example uses the `mf/local` mixin that provides a local mutable stat
to the component.


### Reactive Component

You need to use the `mf/reactive` mixin and `mf/react` (instead of
deref) for deref the atom. Let's see an example:

```clojure
(def count (atom 0))

(mf/def counter
  :mixins [mf/reactive]
  :render
  (fn [own props]
    [:div {:on-click #(swap! count inc)}
      [:span "Clicks: " (mf/react count)]]))

(mf/mount (mf/element counter) js/document.body)
```

### Pure Component

If you have a component that only accepts immutable data structures,
you can use the `mf/memo` mixin for avoid unnecesary renders if
arguments does not change between them.


```clojure
(mf/def title
  :mixins [mf/memo]
  :render
  (fn [_ {:keys [name]}]
    [:div {:class "label"} name]))
```

So if we manuall trigger the component mounting, we will obtain:

```clojure
(mf/mount (mf/element title {:name "ciri"}) body)   ;; first render
(mf/mount (mf/element title {:name "ciri"}) body)   ;; second render: don't be rendered
(mf/mount (mf/element title {:name "geralt"}) body) ;; third render: re-render
(mf/mount (mf/element title {:name "geralt"}) body) ;; forth render:  don't be rendered
```

The `mf/memo` mixin uses `indentical?` for compare props. If you want
equality by value (using the `=` function), you can use `mf/pure`
mixin.

There also `mf/static` mixin that completelly prevents rerendering.


### Lifecycle Methods


Here’s a full list of lifecycle methods supported by rumext:

```clojure
:init           ;; state, props     => state (called once, on component constructor)
:did-catch      ;; state, err, inf  => state (like try/catch for components)
:did-mount      ;; state            => state
:did-update     ;; state, snapshot  => state
:after-render   ;; state            => state (did-mount and did-update alias)
:should-update  ;; old-state, state => bool  (the shouldComponentUpdate)
:will-unmount   ;; state            => state
:derive-state   ;; state            => state (similar to legacy will-update and will-mount)
:make-snapshot  ;; state            => snapshot
```

A mixin is a map with a combination of this methods. And a component
can have as many mixins as you need.

If you don't understand some methods, refer to react documentation:
https://reactjs.org/docs/react-component.html


## Function Components

Function components as it's name says, are defined using plain
functions. Rumext exposes a lightweigh macro over a `fn` that convert
props from js-object to cljs map (shallow) and exposes a facility for
docorate (wrap) with other higher-order components.

Let's see a example of how to define a component:

```clojure
(require '[rumext.alpha :as mf])

(def title
  (mf/fnc title [{:keys [name]}]
    [:div {:class "label"} name]))
```

The `fnc` is a `fn` analogous macro for creating function
components. There are also `defc` macro that behaves in the similar
way to the `defn`:

```clojure
(mf/defc title
  [{:keys [name]}]
  [:div {:class "label"} name])
```

### Higher-Order Components

This is the way you have to extend/add additional functionality to a
function component. Rumext exposes two:

- `mf/wrap-reactive`: same functionality as `mf/reactive` in class
  based components.
- `mf/wrap-memo`: same functionality as `mf/memo` in class based components.

And you can use them in two ways, the traditional one that consists in direct
wrapping a component with an other:

```clojure
(def title
  (mf/wrap-memo
    (mf/fnc title [{:keys [name]}]
      [:div {:class "label"} name])))
```

Or using a special metadata syntax, that does the same thing but with
less call ceremony:

```clojure
(mf/defc title
  {:wrap [mf/wrap-memo]}
  [props]
  [:div {:class "label"} (:name props)])
```

NOTE: The `mf/reactive` higher-order component behind the scenes uses
**React Hooks** as internal primitives for implement the same behavior
as the `mf/reactive` mixin on class components.


### Hooks (React Hooks)

React hooks is a basic primitive that React exposes for add state and
side-effects to functional components. Rumext exposes right now only
three hooks with a ClojureScript based api.


#### use-state (React.useState)

Hook used for maintain a local state and in functional components
replaces the `mf/local` mixin. Calling `mf/use-state` returns an
atom-like object that will deref to the current value and you can call
`swap!` and `reset!` on it for modify its state.

Any mutation will schedule the component to be rerendered.

```clojure
(require '[rumext.alpha as mf])

(mf/defc local-state
  [props]
  (let [local (mf/use-state 0)]
    [:div {:on-click #(swap! local inc)}
      [:span "Clicks: " @local]]))

(mf/mount (mf/element local-state) js/document.body)
```

#### use-ref (React.useRef)

In the same way as `use-state` returns an atom like object. The unique
difference is that updating the ref value does not schedules the
component to rerender.


#### use-effect (React.useEffect)

This is a primitive that allows incorporate probably efectful code
into a functional component:

```clojure
(mf/defc local-timer
  [props]
  (let [local (mf/use-state 0)]
    (mf/use-effect
      :start (fn [] (js/setInterval #(swap! local inc) 1000))
      :end (fn [sem] (js/clearInterval sem)))
    [:div "Counter: " @local]))

(mf/mount (mf/element local-state) js/document.body)
```

The `:start` callback will be called once the component mounts (like
`did-mount`) and the `:end` callback will be caled once the component
will unmounts (like `will-unmount`).

There are an excepction when you pass a `:deps` parameter, that can
change how many times `:start` will be executed:

- `:deps nil` the default behavior explained before.
- `:deps true` the `:start` callback will be executed after each
  render (a combination of `did-mount` and `did-update` class based
  components).
- `:deps [variable1 variable2]`: only execute `:start` when some of
  referenced variables changes.
- `:watch []` the same as `:watch nil`.

NOTE: for avoid vector to js-array conversion, you can pass directly a
js array in `:deps`.


#### use-memo (React.useMemo)

The purpose of this hook is return a memoized value.

Example:

```clojure
(mf/defc sample-component
  [{:keys [x]}]
  (let [v (mf/use-memo {:init #(pow x 10)
                        :deps [x]})]
    [:span "Value is:" v]))
```

On each render, while `x` has the same value, the `v` only will be
calculated once.


#### deref

This is a custom hook, alternative to `mf/wrap-reactive` &
`mf/react`.

The purpose of this hook is reactivelly rerender component on
a reference (atom-like object) changes.

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

#### Raw Hooks

In some circumstances you will want access to the raw react hooks
functions. For this purpose, rumext exposes the following functions:
`useState`, `useRef`, `useMemo` and `useEffect`.


## License ##

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).
