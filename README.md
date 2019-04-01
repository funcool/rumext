# rumext #

An alternative component macros for
[rum](https://github.com/tonsky/rum) that uses
[hicada](https://github.com/rauhs/hicada) as hiccup syntax compiler
instead of [sablono](https://github.com/r0man/sablono).

## Quick Start

Let's see an example of how to use rumext macros fro define
components:

```clojure
(require '[rumext.core :refer-macros [defc defcs]]
         '[rum.core :as rum])

(defc label
  [text]
  [:div {:class "label"} text])
```

## Using Rum

Add to deps.edn:

```
funcool/rumext {:git/url "https://github.com/funcool/rumext.git", :sha "a5c1840779429e2d20a3a88497d5acf4055a1a5e"}
```

## Differences with rum

On the first look, there are no notable differences with rum
macros. The difference comes when mixins and lifecycle methods
are involved; let's see a complete example:

```clojure
(defcs local-state
  "A component docstring/description (optional)."
  {:mixins [(rum/local 0)]
   :init (fn [own props]
           (println "Component initialized"))}
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
a special syntax (only the common `defn` syntax).

And finally, the body is compiled statically (never interprets at
runtime) thanks to **hicada**.


## FAQ

**Can I combine components defined with rum macros and rumext macros?**

Absolutly, rumext only defines an alternative syntax but behind the
scenes it uses the rum internals in order to create components. So
after compilation, rum and rumext components they are practically
identical.


## License ##

``` This Source Code Form is subject to the terms of the Mozilla
Public License, v. 2.0. If a copy of the MPL was not distributed with
this file, You can obtain one at http://mozilla.org/MPL/2.0/.
