(ns tupelo.neo4j
  (:use tupelo.core)
  (:require
    [schema.core :as s]
    [tupelo.neo4j.conversion :as conv]
    [tupelo.schema :as tsk]
    [tupelo.set :as set]
    [tupelo.string :as str]
    )
  (:import
    [java.net URI]
    [java.util.logging Level]
    [org.neo4j.driver GraphDatabase AuthTokens Config AuthToken Driver Session]
    [org.neo4j.driver.exceptions TransientException]
    [org.neo4j.driver.internal.logging ConsoleLogging]
    ))

; A neo4j connection map with the driver under `:driver`
; #todo fork & cleanup from neo4j-clj.core to remove extraneous junk
(def ^:dynamic *neo4j-driver* nil) ; #todo add earmuffs

; a neo4j Session object
(def ^:dynamic *neo4j-session* nil) ; #todo add earmuffs

; for debugging
(def ^:no-doc ^:dynamic *verbose* false) ; #todo add earmuffs

;-----------------------------------------------------------------------------
(defn config [options]
  (let [logging (:logging options (ConsoleLogging. Level/CONFIG))]
    (-> (Config/builder)
      (.withLogging logging)
      (.build))))

(s/defn get-driver :- Driver
  "Returns a Neo4j Driver from an URI. Uses BOLT as the only communication protocol.

   You can connect using a URI or a URI, user, password combination.
   Either way, you can optioninally pass a map of options:

  `:logging`   - a Neo4j logging configuration, e.g. (ConsoleLogging. Level/FINEST)"
  ([uri user pass] (get-driver uri user pass nil))
  ([uri user pass options]
   (let [auth   (AuthTokens/basic user pass)
         config (config options)
         driver (GraphDatabase/driver ^URI uri ^AuthToken auth ^Config config)]
     driver))

  ([uri] (get-driver uri nil))
  ([uri options]
   (let [config (config options)
         driver (GraphDatabase/driver ^URI uri ^Config config)]
     driver)))

;-----------------------------------------------------------------------------
(defn ^:no-doc with-driver-impl
  [[uri user pass & forms]]
  `(binding [tupelo.neo4j/*neo4j-driver* (get-driver (URI. ~uri) ~user ~pass)]
     (with-open [n4driver# tupelo.neo4j/*neo4j-driver*]
       (when *verbose* (spy :with-driver-impl--enter n4driver#))
       ~@forms
       (when *verbose* (spy :with-driver-impl--leave n4driver#)))))

(defmacro with-driver
  "Creates a Neo4j driver (cached as `*neo4j-driver-map*`) for use by the enclosed forms."
  [uri user pass & forms]
  (with-driver-impl (prepend uri user pass forms)))

;-----------------------------------------------------------------------------
(defn ^:no-doc with-session-impl
  [forms]
  `(binding [tupelo.neo4j/*neo4j-session* (.session tupelo.neo4j/*neo4j-driver*)]
     (with-open [n4session# tupelo.neo4j/*neo4j-session*]
       (when *verbose* (spy :sess-open-enter--enter n4session#))
       ~@forms
       (when *verbose* (spy :sess-open-leave--leave n4session#)))))

(defmacro with-session
  "Creates a Neo4j session object (cached as `*neo4j-session*`) for use by the enclosed forms.
  Must be enclosed by a `(with-driver ...)` form."
  [& forms]
  (with-session-impl forms))

;-----------------------------------------------------------------------------
(s/defn run :- tsk/Vec
  "Runs a neo4j cypher command, returning the result as a vector.
   Must be enclosed by a `(with-session ...)` form. Not lazy."
  ([query       ] (vec (conv/neo4j->clj (.run ^Session tupelo.neo4j/*neo4j-session* query))))
  ([query params] (vec (conv/neo4j->clj (.run ^Session tupelo.neo4j/*neo4j-session* query (conv/clj->neo4j params))))))

(s/defn info-map :- tsk/KeyMap
  "Returns a map describing the current Neo4j installation"
  [] (only (run "call dbms.components() yield name, versions, edition
                 unwind versions as version
                 return name, version, edition ;")))

(s/defn neo4j-version :- s/Str
  "Retuns the Neo4j version string"
  [] (grab :version (info-map)))

;-----------------------------------------------------------------------------
(s/defn ^:no-doc apoc-version-impl :- s/Str
  []
  ; may throw if APOC not present
  (grab :ApocVersion (only (run "return apoc.version() as ApocVersion;"))))

(s/defn apoc-version :- s/Str
  "Returns the APOC version string, else `*** APOC not installed ***`"
  []
  (try
    (apoc-version-impl)
    (catch Exception <>
      "*** APOC not installed ***")))

(s/defn apoc-installed? :- s/Bool
  "Returns `true` iff APOC plugin is installed"
  []
  (try
    (let [version-str (apoc-version-impl)]
      ; didn't throw, check version
      (str/increasing-or-equal? "4.0" version-str))
    (catch Exception <>
      false))) ; it threw, so assume not installed

(s/defn nodes-all :- tsk/Vec
  "Returns a vector of all nodes in the DB"
  [] (vec (run "match (n) return n as node;")))

;-----------------------------------------------------------------------------
(s/defn db-names-all :- [s/Str]
  "Returns the names of all databases present"
  []
  (mapv #(grab :name %) (run "show databases")))

(def core-db-names
  "Never delete these DBs! "
  #{"system" "neo4j"})
(s/defn drop-extraneous-dbs! :- [s/Str]
  []
  (let [drop-db-names (set/difference (set (db-names-all)) core-db-names)]
    (doseq [db-name drop-db-names]
      (run (format "drop database %s if exists" db-name)))))

;-----------------------------------------------------------------------------
; Identifies an Neo4j internal index
(def ^:no-doc org-neo4j-prefix "__org_neo4j")
(s/defn ^:no-doc internal-index? :- s/Bool
  "Identifies extraneous indexes (neo4j linux!) are also returned. See unit test for example"
  [idx-map :- tsk/KeyMap]
  (str/contains-str? (grab :name idx-map) org-neo4j-prefix))

(s/defn ^:no-doc extraneous-index? :- s/Bool
  "Identifies extraneous indexes which can exist even in a newly-created, empty db. See unit test for example "
  [idx-map :- tsk/KeyMap]
  (or
    (nil? (grab :labelsOrTypes idx-map))
    (nil? (grab :properties idx-map))))

(s/defn ^:no-doc user-index? :- s/Bool
  "A user-created index (not Neo4j-created). See unit test for example "
  [idx-map :- tsk/KeyMap]
  (not (or
         (internal-index? idx-map)
         (extraneous-index? idx-map))))

(s/defn indexes-all-details :- [tsk/KeyMap]
  "Returns details for all indexes"
  [] (vec (run "show indexes;")))

(s/defn indexes-user-details :- [tsk/KeyMap]
  "Returns details for all user indexes"
  []
  (keep-if #(user-index? %) (indexes-all-details)))

(s/defn indexes-user-names :- [s/Str]
  "Returns the names of all user indexes"
  []
  (mapv #(grab :name %) (indexes-user-details)))

(s/defn index-drop!
  "Drops an index by name"
  [idx-name]
  (run (format "drop index %s if exists" idx-name)))

(s/defn indexes-drop-all!
  "Drops all user indexes"
  []
  (doseq [idx-map (indexes-user-details)]
    (index-drop! (grab :name idx-map))))

;-----------------------------------------------------------------------------
(s/defn constraints-all-details :- [tsk/KeyMap]
  "Returns details for all constraints"
  [] (vec (run "show all constraints;")))

(s/defn constraints-all-names :- [s/Str]
  "Returns the names of all constraints"
  [] (mapv #(grab :name %) (constraints-all-details)))

(s/defn constraint-drop!
  "Drops a constraint by name"
  [cnstr-name]
  (run (format "drop constraint %s if exists" cnstr-name)))

(s/defn constraints-drop-all!
  "Drops all constraints"
  []
  (doseq [cnstr-name (constraints-all-names)]
    (constraint-drop! cnstr-name)))

;-----------------------------------------------------------------------------
(defn delete-all-nodes-simple!
  "Drops all nodes in the DB.  works, but could overflow jvm heap for large db's "
  []
  (vec (run "match (n) detach delete n;")))

(defn delete-all-nodes-apoc!
  "Drops all nodes in the DB.  Uses function `apoc.periodic.iterate` to work in batches.
  Safe for large DBs."
  []
  (vec (run
         (str/quotes->double
           "call apoc.periodic.iterate( 'MATCH (n)  return n',
                                        'DETACH DELETE n',
                                        {batchSize:1000} )
            yield  batches, total
            return batches, total"))))

(defn delete-all-nodes!
  "Delete all nodes & edges in the graph.  Uses apoc.periodic.iterate() if installed."
  []
  (if (apoc-installed?)
    (delete-all-nodes-apoc!)
    (delete-all-nodes-simple!)))

