Demo project to try out requiring ClojureScript dynamically by scanning the file
system from the classpath.

For background see [this conversation](https://clojurians-log.clojureverse.org/cljs-dev/2020-05-29/1590760413.380700)

```
.
├── deps.edn
├── src
│   └── foo
│       ├── autorequire.clj
│       └── main.cljs
└── test
    └── foo
        └── bar_test.cljs
```

deps.edn:

``` clojure
{:paths ["src" "test"]
 :deps {org.clojure/clojurescript {:local/root "/home/arne/github/clojurescript"}}} ;; ffbdf90f5f85a18300fb0554129def32d5809068
```

Macro to load tests, will result in `(require 'foo.bar-test)`

``` clojure
(ns foo.autorequire
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defmacro require-test-namespaces! []
  `(require ~@(->> (io/file "test")
                   file-seq
                   (filter #(str/ends-with? % ".cljs"))
                   (map #(list 'quote (-> %
                                          str
                                          (str/replace #"^test/" "")
                                          (str/replace #"\.cljs$" "")
                                          (str/replace "/" ".")
                                          (str/replace "_" "-")
                                          symbol))))))
```

Main ns which calls the macro

```
(ns foo.main
  (:require-macros [foo.autorequire :refer [require-test-namespaces!]]))

(require-test-namespaces!)

(defn -main []
  (println "in foo.main/-main"))
```

File to be loaded by the macro.

```
(ns foo.bar-test)

(println "in foo.bar-test")
```

Let's try that:

```
$ clj -m cljs.main --repl-env node --output-to main.js --compile foo.main
```

Here's the first problem, instead of `foo.main` this namespace now has the
`cljs.user.main8FC564A` name. This is because when compiling the consequtive
forms, the `:ns*` op overwrites the `ns-name` from the earlier `:ns` op. (Patch
incoming).

```
$ tail -2 .cljs_node_repl/cljs_deps.js
goog.addDependency("../foo/main.js", ['cljs.user.main8FC564A'], ['cljs.core', 'foo.bar_test']);
goog.addDependency("../nodejscli.js", ['cljs.nodejscli'], ['cljs.core', 'goog.object', 'cljs.nodejs']);
```

The second problem is that even though we see the `'foo.bar_test'` dependency,
`foo.bar-test` does not actually get compiled, there is no `bar_test.js`, and no
corresponding entry in `cljs_deps.js`.

```
$ grep bar_test .cljs_node_repl/cl js_deps.js
goog.addDependency("../foo/main.js", ['cljs.user.main8FC564A'], ['cljs.core', 'foo.bar_test']);
```

`main.js` tries to `goog.require("foo.main")`, but that predictably fails
because of the wrong ns name in `cljs_deps.js`.

```
$ node main.js
Error: goog.require could not find: foo.main
```

Interestingly when running the compilation again the ns-name for `foo.main` in
`cljs_deps.js` is now correct, seems that when it uses the cached info from the
filesystem this issue goes away.

```
$ clj -m cljs.main --repl-env node --output-to main.js --compile foo.main
```

```
$ cat .cljs_node_repl/cljs_deps.js | grep foo.main
goog.addDependency("../foo/main.js", ['foo.main'], ['cljs.core']);
```

But there is still no `bar_test.js`, so `node main.js` still fails.

```
➜ tree .cljs_node_repl/foo
.cljs_node_repl/foo
├── bar_test.cljs.cache.json
├── main.cljs
├── main.cljs.cache.json
├── main.js
└── main.js.map
```

```
$ node main.js
Error: goog.require could not find: foo.bar_test
```
