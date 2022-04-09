# rumext #

Simple and Decomplected UI library based on React.


## Using rumext

Add to deps.edn:

```
funcool/rumext {:mvn/version "2022.04.10-141"}
```

## Differences with rum

This project is originated as a friendly fork of
[rum](https://github.com/tonsky/rum) for a personal use but it is
evolved to be a completly independent library that right now does not
depend on it. In any case, many thanks to Tonksy for creating rum.

This is the list of the main differences:

- use function based components instead of class based components.
- a clojurescript friendly abstractions for React Hooks.
- the component body is compiled statically (never interprets at
  runtime thanks to **hicada**).
- performance focused, with a goal to offer almost 0 runtime
  overhead on top of React.

**WARNING**: this is not intended for general use, it is mainly
implemented to be used in [penpot](https://github.com/penpot/penpot)
and released as separated project for conveniendce. Don't expect
compromise for backward compatibility.


## Components

### How to define a component

Function components as it's name says, are defined using plain
functions. Rumext exposes a lighweigh macro over a `fn` that convert
props from js-object to cljs map (shallow) and exposes a facility for
docorate (wrap) with other higher-order components.

Let's see a example of how to define a component:

```clojure
(require '[rumext.alpha :as mf])

(mf/defc title
  [{:keys [name]}]
  [:div {:class "label"} name])
```

If you don't want the props in cljs data structure, you can disable
the props conversion passing `::mf/wrap-props false` as metadata:

```clojure
(require '[goog.object :as gobj])

(mf/defc title
  [props]
  (let [name (gobj/get props "name")]
    [:div {:class "label"} name]))
```

### First steps with hicada hiccup

You may be already familiar with hiccup syntax for defining the react
dom. The intention on this section is explain only the essential part
of it and the peculiarities of hiccada and rumext.

Lets start with simple generic components like `:div`:

```clojure
[:div {:class "foobar"
       :style {:background-color "red"}
       :on-click some-on-click-fn}
  "Hello World"]
```

Until here, nothing new, looks like any hiccup template. The 


As you can observe, looks very familiar. On default components the
props are transformed **recursively** at compile time to a js object
transforming all keys from kebab-case to camelCase (and rename
`:class` to `className`); so the result will look aproximatelly like
this in jsx:

```js
const h = React.createElement;

h("div", {className: "foobar", 
          style: {"backgroundColor": "red"},
          onClick=someFn},
          "Hello World");
```

TODO

### Higher-Order Components

This is the way you have to extend/add additional functionality to a
function component. Rumext exposes one:

- `mf/memo`: analogous to `React.memo`, adds memoization to the
  component based on props comparison.

In order to use the high-order components, you need wrap the component manually
or passing it as a special property in the metadata:

```clojure
(mf/defc title
  {::mf/wrap [mf/wrap-memo]}
  [props]
  [:div {:class "label"} (:name props)])
```

By default `identical?` predicate is used for compare props; this is
how you can pass a custom compare function:

```clojure
(mf/defc title
  {::mf/wrap [#(mf/wrap-memo % =)]}
  [props]
  [:div {:class "label"} (:name props)])
```

If you want create a own high-order component you can use `mf/fnc` macro:

```clojure
(defn some-factory
  [component param]
  (mf/fnc myhighordercomponent
    {::mf/wrap-props false}
    [props]
    [:section
      [:> component props]]))
```


### Hooks (React Hooks)

React hooks is a basic primitive that React exposes for add state and
side-effects to functional components. Rumext exposes right now only
three hooks with a ClojureScript based api.


#### use-state (React.useState)

Hook used for maintain a local state and in functional
components. Calling `mf/use-state` returns an atom-like object that
will deref to the current value and you can call `swap!` and `reset!`
on it for modify its state.

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

#### use-var (React.useRef)

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
      (fn []
        (let [sem (js/setInterval #(swap! local inc) 1000)]
          #(js/clearInterval sem))))
    [:div "Counter: " @local]))

(mf/mount (mf/element local-state) js/document.body)
```

The `use-effect` is a two arity function. If you pass a single
callback function it acts like there are no dependencies, so the
callback will be executed once per component (analgous to `didMount`
and `willUnmount`).

If you want to pass dependencies you have two ways:

- passing an js array
- using `rumext.alpha/deps` helper

```clojure
(mf/use-effect
  (mf/deps x y)
  (fn [] (do-stuff x y)))
```

And finally, if you want to execute it on each render, pass `nil` as
deps (much in the same way as raw useEffect works.


#### use-memo (React.useMemo)

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

There is also the `rumext.alpha/use-callback` for a specific use
cases.


#### deref

A custom hook that adds ractivity to atom changes to the component.

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
`useState`, `useRef`, `useMemo`, `useCallback`, `useLayoutEffect` and
`useEffect`.

#### Other undocumented stuff

- Error boundaries: `mf/catch` high-order component.
- Raw `React.memo`: `mf/memo'`.
- Create element: `mf/element` and `mf/create-element`.


## License ##

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).
