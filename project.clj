(defproject io.tupelo/neo4j "21.10.13-alpha1"
  :dependencies      [
                      [clj-time "0.15.2"]
                      [environ "1.2.0"]
                      [org.clojure/clojure "1.10.3"]
                      [org.neo4j.driver/neo4j-java-driver "4.1.1"]
                     ;[org.neo4j.driver/neo4j-java-driver "4.3.4"] ; #todo upgrade
                      [prismatic/schema "1.1.12"]
                      [tupelo "21.10.06b"]
                     ]
  :plugins           [[com.jakemccrary/lein-test-refresh "0.24.1"]
                      [lein-ancient "0.7.0"]
                      [lein-environ "1.2.0"]
                      ]

  :global-vars       {*warn-on-reflection* false}
  :main              ^:skip-aot demo.core

  :source-paths      ["src/clj"]
  :java-source-paths ["src/java"]
  :test-paths        ["test/clj"]
  :target-path       "target/%s"
  :compile-path      "%s/class-files"
  :clean-targets     [:target-path]

  :profiles          {:dev     {:dependencies []}
                      :uberjar {:aot :all}}

  :jvm-opts          ["-Xms500m" "-Xmx2g"]
)
