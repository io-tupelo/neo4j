  {
   :dev  {:env {:neo4j-uri "bolt://localhost:7687"
                :neo4j-user "neo4j"
                :neo4j-password "secret" }}
   :test  {:env {:neo4j-uri "bolt://localhost:7687"
                :neo4j-user "neo4j"
                :neo4j-password "secret" }}
  :prod {:env {}} ; #todo
  }

