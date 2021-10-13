(ns tupelo.config
  (:use tupelo.core)
  (:require
    [environ.core :as environ]
    ))

(def neo4j-uri (environ/env :neo4j-uri))
(def neo4j-user (environ/env :neo4j-user))
(def neo4j-password (environ/env :neo4j-password))
