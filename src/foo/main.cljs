(ns foo.main
  (:require-macros [foo.autorequire :refer [require-test-namespaces!]]))

(require-test-namespaces!)

(defn -main []
  (println "in foo.main/-main"))
