# rumext #

This is a friendly fork of [rum](https://github.com/tonsky/rum).

## Using rumext

Add to deps.edn:

```
funcool/rumext {:git/url "https://github.com/funcool/rumext.git", :sha "fc554aacd9ea2ea5b72f93b5bcc7cd1002c54064"}
```

## Differences with rum

This is the list of the main differences that rumext introduces (vs rum):

- the syntax for defining components is identical to `defn`;
  rum has it's own syntax (see examples below).
- the component body is compiled statically (never interprets at
  runtime thanks to **hicada**).
- the react class is build lazily (no react class is build until first
  call to the component). This allows reduce load time when you have a
  lot of components but on the "current page" only a few of them are
  used.
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


## Defining a component

Let's see an example of how to use rumext macros for define
components:

```clojure
(require '[rumext.core as rmt :refer-macros [defc defcs]])

(defc label
  [text]
  [:div {:class "label"} text])
```

On the first look, there are no notable differences with rum
macros. The difference comes when mixins and lifecycle methods
are involved; let's see a complete example:

```clojure
(defcs local-state
  "A component docstring/description (optional)."
  {:mixins [(rum/local 0)]
   :init (fn [own props]
           (println "Component initialized")
           own)}
  [state title]
  (let [*count (:rum/local state)]
    [:div
     {:style {"-webkit-user-select" "none"
              "cursor" "pointer"}
      :on-click (fn [_] (swap! *count inc)) }
     title ": " @*count]))
```

As you can observe, rumext `defcs` macro is practically identical to
the clojurescript `defn`:

- it has an optional docstring
- it uses metadata syntax to provide additional lifecycle methods
- it uses `:mixins` entry on metadata to provide additional mixins

This approach allows user redefine lifecycle without creating an
ad-hoc mixin and also allow an easy way to use other mixins without
a special syntax.


## FAQ

**Can I combine components defined with rum macros and rumext macros?**

Absolutly, rumext only defines an alternative syntax but behind the
scenes it uses the rum internals in order to create components and
react classes. So after compilation, rum and rumext components are
practically identical.


## License ##

``` This Source Code Form is subject to the terms of the Mozilla
Public License, v. 2.0. If a copy of the MPL was not distributed with
this file, You can obtain one at http://mozilla.org/MPL/2.0/.
