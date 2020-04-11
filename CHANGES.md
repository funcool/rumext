# Changelog #

## Version 2020.04.11-0

- Use `Symbol` instead of `gensym` on `deref` (faster and more
  compatible with `funcool/okulary`).
- Expose `Profiler`.
- Remove hydrante function.


## Version 2020.04.08-1

- Fix component naming issues when wrap is used.


## Version 2020.04.02-3

- Fix bugs with Fragments.


## Version 2020.04.02-2

- Fix bugs on catch higher-order component.


## Version 2020.04.02-1

- Fix bugs on use-memo and use-callback.
- Fix bugs on catch higher-order component.


## Version 2020.04.01-3

- Simplify `defc` and `fnc` macros.
- Add `catch` higher-order error boundary component.
- Rename `memo` to `memo'`.
- Rename `wrap-memo` to `memo`.
- Keep `wrap-memo` as backward compatible alias.



## Version 2020.04.01-2

- Add `rumext.alpha/memo` as a raw variant of `wrap-memo`.


## Version 2020.04.01-1

- Add `fnc` macro for define anonymous components (useful for define
  higher-order components).
- Depend directrly from react and react-dom from npm. No more cljsjs packages.
- Add printability for Symbol.


## Version 2020.03.24

- Refactor hooks (make they almost 0 runtime cost).
- Remove all old obsolete code.
- Remove macros for define class based components.
- Many performance improvements and code simplification.


## Version 2020.03.23

- Complete rewrite.


## Version 1.0.0

- Initial release.
