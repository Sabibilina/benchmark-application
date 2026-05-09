
-------------------------------------------

### 4.2. Shared persistence and infrastructure guidance

* The system may use polyglot persistence. Each service may choose the persistence technology that best fits its data model, query patterns, and consistency requirements, provided that service data isolation is preserved.  
* Each service that persists state must use its own dedicated persistence layer. Services must not directly access another service’s persistence layer.  
* Acceptable persistence categories include relational, document, key-value/cache, search-oriented, graph, and object storage where appropriate to the service responsibilities.  
* Search may use PostgreSQL full-text search, Elasticsearch, or another documented search-oriented backend that satisfies the requirements.  
* Event-driven communication may use Kafka or another documented message broker that satisfies the requirements.  
* Monitoring should use Prometheus, with Grafana recommended for the admin dashboard.  
* Object storage for simulated streaming payloads may use MinIO or an equivalent documented local object store if needed by the implementation.

### 4.3. Selection constraints

* Each service must use exactly one approved application stack.  
* The final system must include at least three distinct language/framework stacks across the 8 services.  
* Persistence technology may vary across services, but the database-per-service pattern and service data isolation must be preserved.  
* Stack choices and persistence choices must be documented in the final README or implementation notes.  
* If an approved alternative is selected instead of the preferred one, the choice must be briefly justified in the generated documentation.
