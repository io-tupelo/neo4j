(ns tst.demo.indexes
  (:use tupelo.core tupelo.test)
  (:require
    [schema.core :as s]
    [tupelo.config :as config]
    [tupelo.neo4j :as neo4j]
    [tupelo.string :as str]
    [tupelo.schema :as tsk]))

(dotest   ; -focus
  (neo4j/with-driver config/neo4j-uri config/neo4j-user config/neo4j-password  ; URI/username/password

    ; Create a constraint, then an index & compare
    (neo4j/with-session
      (neo4j/drop-extraneous-dbs!)
      (neo4j/run "create or replace database neo4j") ; drop/recreate default db

      ; fresh DB
      (is= 0 (count (neo4j/nodes-all))) ; no nodes
      (is= [] (neo4j/indexes-user-names)) ; no user indexes
      (is= [] (neo4j/constraints-all-details)) ; no user constraints

      (let [create-movie (s/fn [m :- tsk/KeyMap]
                           (neo4j/run "CREATE (m:Movie $Data)   return m as film" m))]

        ; note return type :film set by "return ... as ..."
        (is= [{:film {:title "The Matrix"}}] (create-movie {:Data {:title "The Matrix"}}))
        (is= [{:film {:title "Star Wars"}}] (create-movie {:Data {:title "Star Wars"}}))
        (is= [{:film {:title "Raiders"}}] (create-movie {:Data {:title "Raiders"}}))
        (is= 3 (count (neo4j/nodes-all)))

        ; note return type :flick set by "return ... as ..."
        (is-set= (neo4j/run "MATCH (m:Movie) RETURN m as flick")
          [{:flick {:title "The Matrix"}}
           {:flick {:title "Star Wars"}}
           {:flick {:title "Raiders"}}])

        (is= [] (neo4j/run "create constraint  cnstr_UniqueMovieTitle  on (m:Movie)
                              assert m.title is unique;"))
        (is (submap? ; NOTE:  index entry has more details than constraint entry
              {:entityType    "NODE"
               :labelsOrTypes ["Movie"]
               :name          "cnstr_UniqueMovieTitle"
               :properties    ["title"]
               :type          "UNIQUENESS"}
              (only (neo4j/constraints-all-details))))
        (is= ["cnstr_UniqueMovieTitle"] (neo4j/constraints-all-names))

        ; verify throws if duplicate movie title
        (throws? (create-movie {:Data {:title "Raiders"}}))

        ; Constraints show up as a user index
        (is= (neo4j/indexes-user-names) ["cnstr_UniqueMovieTitle"])
        (is (submap? ; NOTE:  index entry has more details than constraint entry
              {:entityType        "NODE"
               :indexProvider     "native-btree-1.0"
               :labelsOrTypes     ["Movie"]
               :name              "cnstr_UniqueMovieTitle"
               :populationPercent 100.0
               :properties        ["title"]
               :state             "ONLINE"
               :type              "BTREE"
               :uniqueness        "UNIQUE"}
              (only (neo4j/indexes-user-details))))

        ; Adding a redundant index will be ignored due to pre-existing constraint index
        (neo4j/run "create index  idx_MovieTitle  if not exists
                           for (m:Movie) on (m.title);")
        (is= (neo4j/indexes-user-names) ["cnstr_UniqueMovieTitle"])

        ; index never created, so it throws if we try to drop
        (throws? (neo4j/run "drop index  idx_MovieTitle "))

        ; we can drop the constraint
        (is= [] (neo4j/run "drop constraint  cnstr_UniqueMovieTitle"))))

    ; Create an index, then a constraint & compare
    (neo4j/with-session
      (neo4j/drop-extraneous-dbs!)
      (neo4j/run "create or replace database neo4j") ; drop/recreate default db

      (let [create-movie (s/fn [m :- tsk/KeyMap]
                           (neo4j/run "CREATE (m:Movie $Data)   return m as film" m))]

        ; note return type :film set by "return ... as ..."
        (is= [{:film {:title "The Matrix"}}] (create-movie {:Data {:title "The Matrix"}}))
        (is= [{:film {:title "Star Wars"}}] (create-movie {:Data {:title "Star Wars"}}))
        (is= [{:film {:title "Raiders"}}] (create-movie {:Data {:title "Raiders"}}))
        (is= 3 (count (neo4j/nodes-all))))

      ; Adding an index works since no contraint
      (neo4j/run "create index  idx_MovieTitle  for (m:Movie) on (m.title);")
      (is= (neo4j/indexes-user-names) ["idx_MovieTitle"])

      ; cannot create a constraint if pre-existing index
      (throws? (neo4j/run "create constraint  cnstr_UniqueMovieTitle  on (m:Movie)
                              assert m.title is unique;"))
      (neo4j/run "drop index  idx_MovieTitle ")
      (is= [] (neo4j/run "create constraint  cnstr_UniqueMovieTitle  on (m:Movie)
                              assert m.title is unique;"))
      (is= (neo4j/constraints-all-names) ["cnstr_UniqueMovieTitle"])
      (is= (neo4j/indexes-user-names) ["cnstr_UniqueMovieTitle"])

      (neo4j/constraints-drop-all!)
      (is= (neo4j/constraints-all-names) [])
      (is= (neo4j/indexes-user-names) []))

    ))

