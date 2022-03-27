# Changelog #


## Version 2022.01.20.128

- Dependencies updates
- Add with-effect hook/macro.
- Add with-memo hook/macro.

## Version 2021.05.12-1

- Fix incompatibilities with hicada 0.1.9

## Version 2021.05.12-0

- Fix bug in `adapt` with keywords.
- Update hicada to 0.1.9

## Version 2021.01.26-0

- Add `check-props` helper.


## Version 2020.11.27-0

- Add `::mf/forward-ref` metadata and support for multiple arguments for components.


## Version 2020.10.14-1

- Fix issues in previous release.


## Version 2020.10.14-0

- Fix minor issues on previous version related
  to the optimized `create-element` function.


## Version 2020.10.06-0

- Add highly optimized version of create-element.
- Properly memoize result of use-var.
- Update deps.


## Version 2020.08.21-0

- Add `:rumext.alpha/register` and `:rumext.alpha/register-as` component metadata for automatically
  register the component on some atom.


## Version 2020.05.22-1

- Bugfixes.

## Version 2020.05.22-0

- Add context api.
- Fix a memory leak warning on throttle higher-order component.


## Version 2020.05.04-0

- Do not reverse wrappers.
- Minor performance optimizations.
- Add throttle higher-order component.
- Add deferred higher-order component.
- Update documentation.
- Change license to MPL 2.0.


## Version 2020.04.14-1

- Revert microtask changes.


## Version 2020.04.14-0

- Schedule a microtask for adding watcher in `deref` hook.
- Properly return value on use-var hook impl functions.


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
