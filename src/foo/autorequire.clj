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
