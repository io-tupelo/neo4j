(ns tupelo.neo4j.impl
  "This namespace contains the logic to connect to Neo4j instances,
  create and run queries as well as creating an in-memory database for
  testing."
  (:require
    [schema.core :as s]
  )
  (:import
    [java.net URI]
    [java.util.logging Level]
    [org.neo4j.driver GraphDatabase AuthTokens Config AuthToken Driver Session]
    [org.neo4j.driver.exceptions TransientException]
    [org.neo4j.driver.internal.logging ConsoleLogging]
  ))

; obsolete code from the orig lib
(comment

  (defn- make-success-transaction [tx]
    (proxy [org.neo4j.driver.Transaction] []
      (run
        ([q] (.run tx q))
        ([q p] (.run tx q p)))
      (commit [] (.commit tx))
      (rollback [] (.rollback tx))

      ; We only want to auto-success to ensure persistence
      (close []
        (.commit tx)
        (.close tx))))

  (defn get-transaction [^Session session]
    (make-success-transaction (.beginTransaction session)))

  (defn create-query
    "Convenience function. Takes a cypher query as input, returns a function that
    takes a session (and parameter as a map, optionally) and return the query
    result as a map."
    [cypher]
    (fn
      ([sess] (session-run sess cypher))
      ([sess params] (session-run sess cypher params))))

  (defmacro defquery
    "Shortcut macro to define a named query."
    [name ^String query]
    `(def ~name (create-query ~query)))

  (defn retry-times [times body]
    (let [res (try
                {:result (body)}
                (catch TransientException e#
                  (if (zero? times)
                    (throw e#)
                    {:exception e#})))]
      (if (:exception res)
        (recur (dec times) body)
        (:result res))))

  (defmacro with-transaction [connection tx & body]
    `(with-open [~tx (get-transaction (get-session ~connection))]
       ~@body))

  (defmacro with-retry [[connection tx & {:keys [max-times] :or {max-times 1000}}] & body]
    `(retry-times ~max-times
       (fn []
         (with-transaction ~connection ~tx ~@body)))))
