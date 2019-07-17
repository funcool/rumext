# rumext #

This is a friendly fork of [rum](https://github.com/tonsky/rum).

## Using rumext

Add to deps.edn:

```
funcool/rumext {:git/url "https://github.com/funcool/rumext.git",
                :sha "4d6a5f263f2fc37eae6756f5745838c6d946353e"}
```

## Differences with rum

This is the list of the main differences that rumext introduces (vs rum):

- clear distintion between class based components and functional
  (function based components).
- functional components uses React Hooks behind the scenes for provide
  **local state**, **pure components** and **reactive** (rerender
  component on atom change).
- a clojurescript friendly abstractions for React Hooks (look on
  `src/rumext/func.cljs` file).
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
(require '[rumext.core as mx])

(mx/def local-state
  :desc "A component docstring/description (optional)."
  :mixins [(rmt/local 0)]
  :init
  (fn [own props]
    (println "Component initialized")
    own)

  :render
  (fn [own {:keys [title] :as props}]
    (let [*count (::rmt/local state)]
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
(rum/def title
  :mixins [mx/static]
  :render
  (fn [_ {:props [name]}]
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


## Functional Components

Functional components are defined using functions, and exposes a
limited set of functionalities supported by class based tanks to the
combination of **React Hooks** and high-order components.

Let's see a example of how to define a component:

TODO


## License ##

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).
