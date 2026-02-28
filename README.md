

## Start
par defaut, on a le mode prod
`./mvnw spring-boot:run`

pour le local (dev):
`./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"`


## Test coverage
`./mvnw clean test jacoco:report`