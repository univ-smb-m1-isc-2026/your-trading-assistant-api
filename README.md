## Stats:
- 203 tests
- 90% tests coverage 

## Start
par defaut, on a le mode prod
`./mvnw spring-boot:run`

pour le local (dev):
`./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"`


## Test coverage
`./mvnw clean test jacoco:report`


## Auth
Pour l'auth on a la route `/auth/..`

### Register
`/auth/register` avec email, username et password

### Login
`/auth/login` avec email et password


Renvoie un JWT Token valable 24h (pour le moment)