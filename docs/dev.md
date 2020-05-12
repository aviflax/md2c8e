# Developing and Testing md2c8e

This project uses [Kaocha][kaocha] as its test runner.

Each subdirectory under [`test`][test-dir] is a test suite, e.g. `examples`, `integration`,
`property`, etc.


## Running the tests

### From a shell

```shell
# Run all suites
bin/kaocha

# Run a single suite
bin/kaocha integration
```

### From a REPL

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


[kaocha]: https://github.com/lambdaisland/kaocha
[test-dir]: ../test/
