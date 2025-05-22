# rumext

Simple and Decomplected UI library based on React >= 18 focused on performance.

## Installation

Add to `deps.edn`:

```clojure
funcool/rumext
{:git/tag "v2.21"
 :git/sha "072d671"
 :git/url "https://github.com/funcool/rumext.git"}
```

## User Guide

Rumext is a tool to build a web UI in ClojureScript.

It's a thin wrapper on [React](https://react.dev/) >= 18, focused on
performance and offering a Clojure-idiomatic interface.

**API Reference**: http://funcool.github.io/rumext/latest/


It uses Clojure macros to achieve the same goal as [JSX
format](https://react.dev/learn/writing-markup-with-jsx) without using anything
but the plain Clojure syntax. The HTML is expressed in a format inspired
in [hiccup library](https://github.com/weavejester/hiccup), but with its own
implementation.

HTML code is represented as nested arrays with keywords for tags and
attributes. Example:

```clojure
[:div {:class "foobar"
       :style {:background-color "red"}
       :on-click some-on-click-fn}
  "Hello World"]
```

Macros are smart enough to transform attribute names from `lisp-case`
to `camelCase` and renaming `:class` to `className`. So the compiled javacript
code for this fragment could be something like:

```js
React.createElement("div",
                    {className: "foobar",
                     style: {"backgroundColor": "red"},
                     onClick: someOnClickFn},
                    "Hello World");
```

And this is what will be rendered when the app is loaded in a browser:

```html
<div class="foobar"
     style="background-color: red"
     onClick=someOnClickFn>
  Hello World
</div>
```

**WARNING**: it is mainly implemented to be used in
[Penpot](https://github.com/penpot/penpot) and released as separated project
for conveniendce. Don't expect compromise for backwards compatibility beyond
what the penpot project needs.

### Instantiating elements and custom components

#### Passing props

As seen above, when using the [Hiccup-like](https://github.com/weavejester/hiccup)
syntax, you can create a HTML element with a keyword like `:div`, `:span` or
`:p`. You can also specify a map of attributes, that are converted at compile
time into a Javascript object.

**IMPORTANT**: a Javascript plain object is different from a Clojure plain map.
In ClojureScript you can handle mutable JS objects with a specific API, and
convert forth and back to Clojure maps. You can learn more about it in
[ClojureScript Unraveled](https://funcool.github.io/clojurescript-unraveled/#javascript-objects)
book.

Rumext macros have some features to pass properties in a more convenient and
Clojure idiomatic way. For example, when using the `[:div {...}]` syntax, you
do not need to add the `#js` prefix, it's added automatically. There are also
some automatic transformations of property names:

 * Names in `lisp-case` are transformed to `camelCase`.
 * Reserved names like `class` are transformed to React convention, like
   `className`.
 * Names already in `camelCase` are passed directly without transform.
 * Properties that begin with `data-` and `aria-` are also passed directly.
 * Transforms are applied only to `:keyword` properties. You can also send
   string properties, that are not processed anyway.

It's important to notice that this transformations are performed at compile time,
having no impact in runtime performance.

#### Dynamic element names and attributes

There are times when we'll need the element name to be chosen dynamically or
constructed at runtime; the props to be built dynamically or created as an
element from a user-defined component.

For this purpose, Rumext exposes a special macro: `:>`, a general-purpose
handler for passing dynamically defined props to DOM native elements or
creating elements from user-defined components.

To define the element dynamically, just pass a variable with the name as a
first parameter of `:>`.

```clojure
(let [element (if something "div" "span")]
  [:> element {:class "foobar"
               :style {:background-color "red"}
               :on-click some-on-click-fn}
    "Hello World"])
```

To give a dynamic map of properties, you may also give a variable as a
second parameter:

```clojure
(let [props #js {:className "fooBar"
                 :style #js {:backgroundColor "red"}
                 :onClick some-on-click}]
  [:> "div" props
    "Hello World"])
```

**IMPORTANT** if you define the attributes dynamically, outside the `:>` macro,
there are no automatic transformations. So you need to define the map as a
plain Javascript object with the `#js` prefix or any other way. You also need
to use `camelCase` names and remember to use `className` instead of `class`,
for example.

There are a couple of utilities for managing dynamic attributes in a more
convenient way.

##### `mf/spread-props`

A macro that allows performing a merge between two props data structures using
the JS spread operator (`{...props1, ...props2}`). This macro also performs
name transformations if you pass a literal map as a second parameter.

It is commonly used this way:

```clojure
(mf/defc my-label*
  [{:keys [name class on-click] :rest props}]
  (let [class (or class "my-label")
        props (mf/spread-props props {:class class})]
    [:span {:on-click on-click}
      [:> :label props name]]))
```

##### `mf/props`

A helper macro to create a Javascript props object from a Clojure map,
applying name transformations.

An example of how it can be used and combined with `mf/spread-props`:

```clojure
(mf/defc my-label*
  [{:keys [name class on-click] :rest props}]
  (let [class (or class "my-label")
        new-props (mf/props {:class class})
        all-props (mf/spread-props props new-props)]
    [:span {:on-click on-click}
      [:> :label props name]]))
```


##### `mf/map->props`

In some cases you will need to make props from a dynamic Clojure object. You
can use `mf/map->props` function for it, but be aware that it makes the
conversion to Javascript and the names transformations in runtime, so it adds
some overhead in each render. Consider it if performance is important.

```clojure
(let [clj-props {:class "my-label"}
      props (mf/map->props clj-props)]
  [:> :label props name])
```

#### Instantiating a custom component

You can pass to `:>` macro the name of a custom component (see [below](#creating-a-react-custom-component))
to create an instance of it:

```clojure
(mf/defc my-label*
  [{:keys [name class on-click] :rest props}]
    [:span {:on-click on-click}
      [:> :label props name]])

(mf/defc other-component*
  []
  [:> my-label* {:name "foobar" :on-click some-fn}])
```

### Creating a React custom component

The `defc` macro is the basic block of a Rumext UI. It's a lightweight utility
that generates a React **function component** and adds some adaptations for it
to be more convenient to ClojureScript code, like `camelCase` conversions and
reserved name changes as explained [above](#passing-props).

For example, this defines a React component:

```clojure
(require '[rumext.v2 :as mf])

(mf/defc title*
  [{:keys [label-text] :as props}]
  [:div {:class "title"} label-text])
```

The compiled javascript for this block will be similar to what would be
obtained for this JSX block:

```js
function title({labelText}) {
  return (
    <div className="title">
      {labelText}
    </div>
  );
}
```

**NOTE**: the `*` in the component name is a mandatory convention for proper
visual distinction of React components and Clojure functions. It also enables
the current defaults on how props are handled. If you don't use the `*` suffix,
the component will behave in legacy mode (see the [FAQs](#faq) below).

The component created this way can be mounted onto the DOM:

```clojure
(ns myname.space
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]))

(def root (mf/create-root (dom/getElement "app")))
(mf/render! root (mf/html [:> title* {:label-text "hello world"}]))
```

Or you can use `mf/element`, but in this case you need to give the
attributes in the raw Javascript form, because this macro does not have
automatic conversions:

```clojure
(ns myname.space
  (:require
   [goog.dom :as dom]
   [rumext.v2 :as mf]))

(def root (mf/create-root (dom/getElement "app")))
(mf/render! root (mf/element title* #js {:labelText "hello world"}))
```

### Reading component props & destructuring

When React instantiates a function component, it passes a `props` parameter
that is a map of the names and values of the attributes defined in the calling
point.

Normally, Javascript objects cannot be destructured. But the `defc` macro
implements a destructuring functionality, that is similar to what you can do
with Clojure maps, but with small differences and convenient enhancements for
making working with React props and idioms easy, like `camelCase` conversions
as explained [above](#passing-props).

```clojure
(mf/defc title*
  [{:keys [title-name] :as props}]
  (assert (object? props) "expected object")
  (assert (string? title-name) "expected string")
  [:label {:class "label"} title-name])
```

If the component is called via the `[:>` macro (explained [above](#dynamic-element-names-and-attributes)),
there will be two compile-time conversion, one when calling and another one when
destructuring. In the Clojure code all names will be `lisp-case`, but if you
inspect the generated Javascript code, you will see names in `camelCase`.

#### Default values

Also like usual destructuring, you can give default values to properties by
using the `:or` construct:

```clojure
(mf/defc color-input*
  [{:keys [value select-on-focus] :or {select-on-focus true} :as props}]
  ...)
```

#### Rest props

An additional idiom (specific to the Rumext component macro and not available
in standard Clojure destructuring) is the ability to obtain an object with all
non-destructured props with the `:rest` construct. This allows to extract the
props that the component has control of and leave the rest in an object that
can be passed as-is to the next element.

```clojure
(mf/defc title*
  [{:keys [name] :rest props}]
  (assert (object? props) "expected object")
  (assert (nil? (unchecked-get props "name")) "no name in props")

  ;; See below for the meaning of `:>`
  [:> :label props name])
```

#### Reading props without destructuring

Of course the destructure is optional. You can receive the complete `props`
argument and read the properties later. But in this case you will not have
the automatic conversions:

```clojure
(mf/defc color-input*
  [props]
  (let [value            (unchecked-get props "value")
        on-change        (unchecked-get props "onChange")
        on-blur          (unchecked-get props "onBlur")
        on-focus         (unchecked-get props "onFocus")
        select-on-focus? (or (unchecked-get props "selectOnFocus") true)
        class            (or (unchecked-get props "className") "color-input")
```

The recommended way of reading `props` javascript objects is by using the
Clojurescript core function `unchecked-get`. This is directly translated to
Javascript `props["propName"]`. As Rumext is performance oriented, this is the
most efficient way of reading props for the general case. Other methods like
`obj/get` in Google Closure Library add extra safety checks, but in this case
it's not necessary since the `props` attribute is guaranteed by React to have a
value, although it can be an empty object.

#### Forwarding references

In React there is a mechanism to set a reference to the rendered DOM element, if
you need to manipulate it later. Also it's possible that a component may receive
this reference and gives it to a inner element. This is called "forward referencing"
and to do it in Rumext, you need to add the `forward-ref` metadata. Then, the
reference will come in a second argument to the `defc` macro:

```clojure
(mf/defc wrapped-input*
  {::mf/forward-ref true}
  [props ref]
  (let [...]
    [:input {:style {...}
             :ref ref
             ...}]))
```

In React 19 this will not be necessary, since you will be able to pass the ref
directly inside `props`. But Rumext currently only support React 18.

### Props Checking

The Rumext library comes with two approaches for checking props:
**simple** and **malli**.

Let's start with the **simple**, which consists of simple existence checks or
plain predicate checking. For this, we have the `mf/expect` macro that receives
a Clojure set and throws an exception if any of the props in the set has not
been given to the component:

```clojure
(mf/defc button*
  {::mf/expect #{:name :on-click}}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

The prop names obey the same rules as the destructuring so you should use the
same names.

Sometimes a simple existence check is not enough; for those cases, you can give
`mf/expect` a map where keys are props and values are predicates:

```clojure
(mf/defc button*
  {::mf/expect {:name string?
                :on-click fn?}}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

If that is not enough, you can use `mf/schema` macro that supports
**[malli](https://github.com/metosin/malli)** schemas as a validation
mechanism for props:

```clojure
(def ^:private schema:props
  [:map {:title "button:props"}
   [:name string?]
   [:class {:optional true} string?]
   [:on-click fn?]])

(mf/defc button*
  {::mf/schema schema:props}
  [{:keys [name on-click]}]
  [:button {:on-click on-click} name])
```

**IMPORTANT**: The props checking obeys the `:elide-asserts` compiler
option and by default, they will be removed in production builds if
the configuration value is not changed explicitly.

### Hooks

You can use React hooks as is, as they are exposed by Rumext as
`mf/xxx` wrapper functions. Additionaly, Rumext offers several
specific hooks that adapt React ones to have a more Clojure idiomatic
interface.

You can use both one and the other interchangeably, depending on which
type of API you feel most comfortable with. The React hooks are exposed
as they are in React, with the function name in `camelCase`, and the
Rumext hooks use the `lisp-case` syntax.

Only a subset of available hooks is documented here; please refer to
the [React API reference
documentation](https://react.dev/reference/react/hooks) for detailed
information about available hooks.

#### `use-state`

This is analogous to the `React.useState`. It offers the same
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

This is functionally equivalent to using the React hook directly:

```clojure
(mf/defc local-state*
  [props]
  (let [[counter update-counter] (mf/useState 0)]
    [:div {:on-click (partial update-counter #(inc %))}
      [:span "Clicks: " counter]]))
```

#### `use-var`

In the same way as `use-state` returns an atom-like object. The unique
difference is that updating the ref value does not schedule the
component to rerender. Under the hood, it uses the `useRef` hook.

**DEPRECATED:** should not be used

#### `use-effect`

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

- passing a JS array as a first argument (like in React but with
    inverted order).
- using the `rumext.v2/deps` helper:

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

Obviously, you can also use the React hook directly via `mf/useEffect`.

#### `use-memo`

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

#### `use-fn`

Is a special case of `use-memo`in that the memoized value is a 
function definition.

An alias for `use-callback`, that is a wrapper on `React.useCallback`.

#### `deref`

A Rumext custom hook that adds reactivity to atom changes to the
component. Calling `mf/deref` returns the same value as the Clojure
`deref`, but also sets a component rerender when the value changes.

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

### Higher-Order Components

React allows to create a component that adapts or wraps another component
to extend it and add additional functionality. Rumext includes a convenient
mechanism for doing it: the `::mf/wrap` metadata.

Currently Rumext exposes one such component:

- `mf/memo`: analogous to `React.memo`, adds memoization to the
  component based on props comparison. This allows to completely
  avoid execution to the component function if props have not changed.

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

For more convenience, Rumext has a special metadata `::mf/memo` that
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

### FAQ

#### Differences with RUM

This project was originated as a friendly fork of
[rum](https://github.com/tonsky/rum) for a personal use but it later
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


#### Why the import alias is `mf` in the examples?

The usual convention of importing RUM project was to use `rum/defc` or
`m/defc`. For Rumext the most straightforward abbreviation would have been
`mx/defc`. But that preffix was already use for something else. So finally we
choose `mf/defc`. But this is not mandatory, it's only a convention we follow
in this manual and in Penpot.


#### What is the legacy mode?

In earlier versions of Rumext, components had a default behavior of
automatically converting the `props` Javascript object coming from
React to a Clojure object, so it could be read by normal destructuring
or any other way of reading objects.

Additionally you could use `:&` handler instead of `:>` to give a
Clojure object that was converted into Javascript for passing it to
React.

But both kind of transformations were done in runtime, thus adding
the conversion overhead to each render of the compoennt. Since Rumex
is optimized for performance, this behavior is now deprecated. With
the macro destructuring and other utilities explained above, you can
do argument passing almost so conveniently, but with all changes done
in compile time.

Currently, components whose name does not use `*` as a suffix behave
in legacy mode. You can activate the new behavior by adding the
`::mf/props :obj` metadata, but all this is considered deprecated now.
All new components should use `*` in the name.

## License

Licensed under MPL-2.0 (see [LICENSE](LICENSE) file on the root of the repository)
