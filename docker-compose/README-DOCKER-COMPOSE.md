# JHipster generated Docker-Compose configuration

## Usage

Launch all your infrastructure by running: `docker compose up -d`.

## Configured Docker services

### Service registry and configuration server:

- [JHipster Registry](http://localhost:8761)

### Applications and dependencies:

- gateway (gateway application)
- gateway's postgresql database
- gateway's elasticsearch search engine
- announceService (microservice application)
- announceService's postgresql database
- announceService's elasticsearch search engine
- orderService (microservice application)
- orderService's postgresql database
- catalogService (microservice application)
- catalogService's postgresql database

### Additional Services:

- Kafka
- Zookeeper
- [Keycloak server](http://localhost:9080)
