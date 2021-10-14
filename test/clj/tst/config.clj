(ns tst.config
  (:use tupelo.core)
  (:require
    [environ.core :as environ]
    ))

(def neo4j-uri (environ/env :neo4j-uri))
(def neo4j-username (environ/env :neo4j-username))
(def neo4j-password (environ/env :neo4j-password))
