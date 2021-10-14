(ns tst.tupelo.neo4j
  (:use tupelo.core tupelo.test)
  (:require
    [tst.config :as config]
    [tupelo.neo4j :as neo4j]
    [tupelo.string :as str]
    ))

(dotest
  (neo4j/with-driver config/neo4j-uri config/neo4j-username config/neo4j-password ; uri/username/password

    (neo4j/with-session
      (let [vinfo (neo4j/info-map)]
        ; sample vinfo:  {:name "Neo4j Kernel" :version "4.2-aura" :edition "enterprise"}
        ; sample vinfo:  {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"}
        (with-map-vals vinfo [name version edition]
          (is= name "Neo4j Kernel")
          (is= edition "enterprise")
          (is (str/increasing-or-equal? "4.2" version))))
      (is (str/increasing-or-equal? "4.2" (neo4j/neo4j-version)))
      (is (neo4j/apoc-installed?)) ; verify APOC is present
      (is (str/increasing-or-equal? "4.2" (neo4j/apoc-version))))

    (neo4j/with-session
      (neo4j/drop-extraneous-dbs!)

      ; "system" db is always present.  "neo4j" db is default DB name
      (is-set= (neo4j/db-names-all) #{"system" "neo4j"})

      (neo4j/run "create or replace database neo4j") ; drop/recreate default db
      (neo4j/run "create or replace database SPRINGFIELD") ; make a new DB

      ; NOTE: Dot `.` is legal a char in a DB name.
      (neo4j/run "create or replace database some.hierarchical.db.name")

      ; NOTE: Underscore `_` & hyphen `-` are illegal chars in DB name
      (throws? (neo4j/run "create or replace database SPRING-FIELD"))
      (throws? (neo4j/run "create or replace database SPRING_FIELD"))

      (is-set= (neo4j/db-names-all) #{"system" "neo4j" "springfield" "some.hierarchical.db.name"}) ; NOTE:  all lowercase

      ; use default db "neo4j"
      (neo4j/run "CREATE (u:Jedi $Hero)  return u as padawan"
        {:Hero {:first-name "Luke" :last-name "Skywalker"}})
      (is= (neo4j/run "match (n) return n as Jedi ")
        [{:Jedi {:first-name "Luke", :last-name "Skywalker"}}])

      ; use "springfield" db (DB name always coerced to lowercase by neo4j)
      (is= (only ; CamelCase DB name works
             (neo4j/run "use SpringField
                          create (p:Person $Resident) return p as Duffer" ; `as Duffer` => returned map key
               {:Resident {:first-name "Homer" :last-name "Simpson"}}))
        {:Duffer {:first-name "Homer", :last-name "Simpson"}})

      ; SCREAMINGCASE DB name works
      (is= (neo4j/run "use SPRINGFIELD
                        match (n) return n as Dummy")
        [{:Dummy {:first-name "Homer", :last-name "Simpson"}}])

      (neo4j/run "drop database SpringField if exists")
      )))

(comment
  (neo4j/delete-all-nodes!)
  (neo4j/constraints-drop-all!)
  (neo4j/indexes-drop-all!))

(dotest   ; -focus
  (let [idx-normal     {:properties        (quote ("title"))
                        :populationPercent 0.0
                        :name              "idx_MovieTitle"
                        :type              "BTREE"
                        :state             "POPULATING"
                        :uniqueness        "NONUNIQUE"
                        :id                3
                        :indexProvider     "native-btree-1.0"
                        :entityType        "NODE"
                        :labelsOrTypes     (quote ("Movie"))}
        idx-extraneous {:entityType        "NODE"
                        :id                1
                        :indexProvider     "token-lookup-1.0"
                        :labelsOrTypes     nil
                        :name              "index_343aff4e"
                        :populationPercent 100.0
                        :properties        nil
                        :state             "ONLINE"
                        :type              "LOOKUP"
                        :uniqueness        "NONUNIQUE"}
        idx-internal {:properties        nil
                      :populationPercent 100.0
                      :name              "__org_neo4j_schema_index_label_scan_store_converted_to_token_index"
                      :type              "LOOKUP"
                      :state             "ONLINE"
                      :uniqueness        "NONUNIQUE"
                      :id                1
                      :indexProvider     "token-lookup-1.0"
                      :entityType        "NODE"
                      :labelsOrTypes     nil}
        sample-idxs [idx-normal idx-extraneous idx-internal]
        ]
    (is= [false true true] (mapv neo4j/extraneous-index? sample-idxs))
    (is= [false false true] (mapv neo4j/internal-index? sample-idxs))
    (is= [true false false] (mapv neo4j/user-index? sample-idxs))))

;-----------------------------------------------------------------------------
#_(dotest
    (spy-pretty :impl
      (with-connection-impl '[
                              (URI. "bolt://localhost:7687") "neo4j" "secret"
                              (form1 *neo4j-conn-map*)
                              (form2)
                              ]))
    )

#_(dotest
    (with-connection "bolt://localhost:7687" "neo4j"
      "secret"
      ; (println :aa NEOCONN )
      (println :version (neo4j/info-map *neo4j-conn-map*))
      ; (println :zz NEOCONN )
      ))

;-----------------------------------------------------------------------------

#_(dotest-focus
    (spy-pretty :impl
      (with-session-impl '[
                           (form1 *neo4j-session*)
                           (form2)
                           ])))

#_(dotest-focus
    (with-connection "bolt://localhost:7687" "neo4j"
      "secret"
      ; (println :use-00 NEOCONN )
      (with-session
        ; (println :use-aa SESSION )
        (newline)
        (println :use-version (get-vers))
        (newline)
        (flush)
        ; (println :use-zz SESSION )
        )
      ; (println :use-99 NEOCONN )
      ))



