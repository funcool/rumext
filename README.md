# rumext #

This is a friendly fork of [rum](https://github.com/tonsky/rum).

## Using rumext

Add to deps.edn:

```
funcool/rumext {:git/url "https://github.com/funcool/rumext.git",
                :sha "303c5d5db489dd74ee6597531e46127f8be5d984"}
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
(require '[rumext.alpha as mx])

(mx/def local-state
  :desc "A component docstring/description (optional)."
  :mixins [(mx/local 0)]
  :init
  (fn [own props]
    (println "Component initialized")
    own)

  :render
  (fn [own {:keys [title] :as props}]
    (let [*count (::mx/local state)]
      [:div {:on-click #(swap! *count inc)}
        [:span title ": " @*count]])))
```

This example uses the `mx/local` mixin that provides a local mutable stat
to the component.


### Reactive Component

You need to use the `mx/reactive` mixin and `mx/react` (instead of
deref) for deref the atom. Let's see an example:

```clojure
(def count (atom 0))

(mx/def counter
  :mixins [mx/reactive]
  :render
  (fn [own props]
    [:div {:on-click #(swap! count inc)}
      [:span "Clicks: " (mx/react count)]]))

(mx/mount (counter) js/document.body)
```

### Pure Component

If you have a component that only accepts immutable data structures,
you can use the `mx/static` mixin for avoid unnecesary renders if
arguments does not change between them.


```clojure
(mx/def title
  :mixins [mx/static]
  :render
  (fn [_ {:keys [name]}]
    [:div {:class "label"} name]))
```

So if we manuall trigger the component mounting, we will obtain:

```clojure
(mx/mount (title "ciri") body)   ;; first render
(mx/mount (title "ciri") body)   ;; second render: don't be rendered
(mx/mount (title "geralt") body) ;; third render: re-render
(mx/mount (title "geralt") body) ;; forth render:  don't be rendered
```

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
(require '[rumext.alpha :as mx])

(def title
  (mx/fnc title [{:keys [name]}]
    [:div {:class "label"} name]))
```

The `fnc` is a `fn` analogous macro for creating function
components. There are also `defnc` macro that behaves in the similar
way to the `defn`:

```clojure
(mx/defnc title
  [{:keys [name]}]
  [:div {:class "label"} name])
```

Take care that function component macros does not returs factories,
they return directly the component function. So you need to wrap it
yourself in a react element or use the hicada facilities for it:

```clojure
;; mounting
(mx/mount (mx/element title {:name "foobar"}) js/document.body)

;; using it in other component
(mx/defnc other-component
  [props]
  [:section
    [:& title {:name "foobar"}]])
```


### Higher-Order Components

This is the way you have to extend/add additional functionality to a
function component. Rumext exposes two:

- `mx/reactive`: same functionality as `mx/reactive` in class based components.
- `mx/memo`: same functionality as `mx/static` in class based components.

And you can use them in two ways, the traditional one that consists in direct
wrapping a component with an other:

```clojure
(def title
  (mx/memo
    (mx/fnc title [{:keys [name]}]
      [:div {:class "label"} name])))
```

Or using a special metadata syntax, that does the same thing but with
less call ceremony:

```clojure
(mx/defnc title
  {:wrap [mx/memo]}
  [props]
  [:div {:class "label"} (:name props)])
```

NOTE: The `mx/reactive` higher-order component behind the scenes uses
**React Hooks** as internal primitives for implement the same behavior
as the `mx/reactive` mixin on class components.


### Hooks (React Hooks)

React hooks is a basic primitive that React exposes for add state and
side-effects to functional components. Rumext exposes right now only
three hooks with a ClojureScript based api.

#### useState

Hook used for maintain a local state and in functional components
replaces the `mx/local` mixin. Calling `mx/use-state` returns an
atom-like object that will deref to the current value and you can call
`swap!` and `reset!` on it for modify its state.

Any mutation will schedule the component to be rerendered.

```clojure
(require '[rumext.alpha as mx])

(mx/defnc local-state
  [props]
  (let [local (mx/use-state 0)]
    [:div {:on-click #(swap! local inc)}
      [:span "Clicks: " @local]]))

(mx/mount (mx/element local-state) js/document.body)
```

#### useEffect

This is a primitive that allows incorporate probably efectful code
into a functional component.

```clojure
(mx/defnc local-timer
  [props]
  (let [local (mx/use-state 0)]
    (mx/use-effect
      :start (fn [] (js/setInterval #(swap! local inc) 1000))
      :end (fn [sem] (js/clearInterval sem)))
    [:div "Counter: " @local]))

(mx/mount (mx/element local-state) js/document.body)
```

The `:start` callback will be called once the component mounts (like
`did-mount`) and the `:end` callback will be caled once the component
will unmounts (like `will-unmount`).

There are an excepction when you pass a `:watch` parameter, that can
change how many times `:start` will be executed:

- `:watch nil` the default behavior explained before.
- `:watch true` the `:start` callback will be executed after each
  render (a combination of `did-mount` and `did-update` class based
  components).
- `:watch [variable1 variable2]`: only execute `:start` when some of
  referenced variables changes.
- `:watch []` the same as `:watch nil`.


## License ##

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).
