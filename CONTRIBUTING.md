# Contributing

These contribution guidelines are inspired by
[https://github.com/americanexpress/nodes/blob/master/CONTRIBUTING.md](https://github.com/americanexpress/nodes/blob/master/CONTRIBUTING.md)
and [https://chris.beams.io/posts/git-commit/](https://chris.beams.io/posts/git-commit/).

## Who decides what gets merged?

This app is authored by an internal product team at "die tageszeitung". There is a feature plan and a dedicated team implementing those.
So to save potential contributers the disappointment of a disregarded submission we'd like to ask anyone with ideas to propose this feature to app@taz.de.
If the product team then decides to include the feature we are more than happy to accept pull requests.

## Code Style Guidelines

* IDs used in the XML layouts MUST be in `snake_case`
* Variable names in in the Kotlin code MUST use `camelCase`.
* To improve readability variable names SHOULD be verbose
  e.g. `webView` instead of `wv`.
* [Synthetic imports](https://kotlinlang.org/docs/tutorials/android-plugin.html#view-binding) MAY be used to import view components from used XML layouts.

## Submission Guidelines

* name branches according to following schema

  ```
  <type>/<scope>/<reference?>
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

  Reference is optional, if the contents of that branch is related to a reference of a tracking system, you should put it there.

  Examples:

  ```
  feat/drawer
  feat/newThing/#31
  style/cleanup/#122
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
