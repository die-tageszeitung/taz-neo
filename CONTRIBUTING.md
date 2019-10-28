# Contributing

These contribution guidelines are inspired by
[https://github.com/americanexpress/nodes/blob/master/CONTRIBUTING.md](https://github.com/americanexpress/nodes/blob/master/CONTRIBUTING.md)
and [https://chris.beams.io/posts/git-commit/](https://chris.beams.io/posts/git-commit/).

## Code Style Guidelines

* Xml IDs MUST be in `snake_case`, variable names — in `camelCase`.
* To improve readability, whenever applicable, we wirte out variable names:
  e.g. `webView` instead of `wv`.
* We use synthetic imports of views (i.e. use directly the viewID from the xml
  layouts for referencing the corresponding views in the Kotlin code).

## Submission Guidelines

* name branches according to following schema

  ```
  <type>/<scope>
  ```

  where type is one of:
  * feat (new feature for the user)
  * fix (bug fix for the user, not a fix to build scripts)
  * docs (changes to documentation)
  * style (formatting, missing semi colons, etc; no functional code change)
  * refactor (refactoring production code, eg. renaming a variable)
  * test (adding missing tests, refactoring tests; no production code change)
  * chore (updating build/env/packages, etc; no production code chang

  The scope describes the affected code.
  The descriptor may be a route, component, feature, utility, etc.
  It should be one word or camelCased, if needed.

  Examples:

  ```
  feat/drawer
  refactor/webView
  ```

* review and test code before submitting a pull request

## Git Commit Guidelines

Format commit messages according to the following schema:

```
subject

body

footer
```

### Subject

* Whenever possible, limit the subject line to 50 characters (rule of a thumb).
* Capitalise the subject line.
* Do not end the subject line with a period (saves characters).
* Use the imperative mode in the subject line (e.g. `merge` instead of `merged`,
  `update` instead of `updated`).
* A properly formed Git commit subject line should always be able to complete the
  following sentence:

```
    If applied, this commit will <your subject line here>
```

E.g.:

```
    If applied, this commit will remove deprecated methods
```

### Body and Footer (optional)

The body and footer should wrap at 80 characters.

The body describes the commit in more detail and should not be more than 1
paragraph (3-5 sentences).
Details are important, but too much verbosity can inhibit understanding and
productivity -- keep it clear and concise.
Use it to explain *what* and *why* vs. *how*.

The footer should only reference Pull Requests or Issues associated with the
commit.

### Piecing It All Together

Below is an example of a full commit message that includes a header, a body,
and a footer:

```
Add prop (isActive)

NavItem now supports an "isActive" property.
This property is used to control the styling of active
navigation links.

Closes #21

```
