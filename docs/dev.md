# Developing and Testing md2c8e


## Dependencies

* [Java][adoptopenjdk] 11+
* [Clojure][clojure] 1.10.1
* [Pandoc][pandoc]

### MacOS Quick Start

With [Homebrew][homebrew]:

```shell
brew install java clojure/tools/clojure pandoc
```


## Starting a REPL

```shell
# From the root of the project
clj -A:dev:test
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


[adoptopenjdk]: https://adoptopenjdk.net/
[clojure]: https://clojure.org/
[homebrew]: https://brew.sh/
[kaocha]: https://github.com/lambdaisland/kaocha
[pandoc]: https://pandoc.org/
[test-dir]: ../test/
