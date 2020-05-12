  # Developing and Testing md2c8e


## Dependencies

* [Java][adoptopenjdk] 11+
* [Clojure][clojure] 1.10.1
* [Pandoc][pandoc]
* [clj-kondo][clj-kondo] (for [linting][linting])

### MacOS Quick Start

With [Homebrew][homebrew]:

```shell
# NB: you may already have a different Java installed, or you may wish to
# install a different Java than the default Homebrew formula installs. If so,
# remove `java` from the command below and install the Java of your choice,
# if necessary.
brew install java clojure/tools/clojure pandoc borkdude/brew/clj-kondo
```


## Convenience Scripts

These use cases are described in more detail below, but here’s a quick list of our convenience
scripts:

* `bin/repl` starts a [REPL][repl]
* `bin/kaocha` runs all the test suites
* `bin/kondo` [lints][linting] the source code


## Starting a REPL

```shell
# From the root of the project
clj -A:dev:test

# Or with the convenience script:
bin/repl
```


## Testing

This project uses [Kaocha][kaocha] as its test runner.

Each subdirectory under [`test`][test-dir] is a test suite, e.g. `examples`, `integration`,
`property`, etc.

### Running the tests

#### From a shell

```shell
# Run all suites
bin/kaocha

# Run a single suite
bin/kaocha integration
```

#### From a REPL

```clojure
(use 'kaocha.repl)

; Run all suites
(run-all)

; Run a single suite
(run :unit)

; Run a single namespace
(run 'md2c8e.core-test)

; Run a single test var
(run 'md2c8e.core-test/test-readme?)
```


## Linting

This project currently uses [clj-kondo][clj-kondo] for [linting][linting] — running basic sanity
checks on the source code.

If you’re on MacOS, you can install it easily via [Homebrew][homebrew]:

```shell
brew install borkdude/brew/clj-kondo
```

You can run it via:

```shell
bin/kondo
```


[adoptopenjdk]: https://adoptopenjdk.net/
[clj-kondo]: https://github.com/borkdude/clj-kondo/
[clojure]: https://clojure.org/
[homebrew]: https://brew.sh/
[kaocha]: https://github.com/lambdaisland/kaocha
[linting]: https://en.wikipedia.org/wiki/Lint_(software)
[pandoc]: https://pandoc.org/
[repl]: https://en.wikipedia.org/wiki/REPL
[test-dir]: ../test/
