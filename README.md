# hapi-fhir-jpaserver-oauth-21cfr11

Forked from https://github.com/AriHealth/hapi-fhir-jpaserver-oauth with gratitude to Atos Spain S.A. Unipersonal.

## Description

This is an example implementation of the draft [21 CFR Part 11-compliant FHIR PRO Implementation Guide](https://chb.github.io/21cfr11pro-ig/). 
It centers around an open-source implementation of the FHIR server specification in Java based on [HAPI FHIR](http://hapifhir.io/). 

HAPI FHIR CDR with support for several databases (Derby, MySQL, MariaDB and PostgreSQL) and OAuth. The server and 
associated containers provide an example implementation for a 21 CFR Part 11 system, where an enrolled and authenticated 
FHIR client is provided an identity certificate that they can use to sign resources to provide an assuracen about 
their provenance. The server also logs transactions to an immutable cryptographic journal to provide a tamper-evident 
record of all transactions and their provenance, and a complete audit trail. 

A docker-compose file is included that boots the back-end services for [client](https://bitbucket.org/ihlchip/fhir-21cfr11pro-client-example) 
testing:

* This HAPI-FHIR Oauth server (http://localhost:8080)
* Keycloak - a full-featured OpenID ID provider  (http://localhost:9090)
* keycloak-auth-bch - a gateway web service app that is an OAuth resource server and provides `/login`,  `/user` 
(authenticated user information) and `/sign` for processing ID certificate signing requests. 
(Swagger test at http://localhost:8081/swagger-ui.html, REST endpoints at `http://localhost:8081/login`,  `.../user` and `.../sign`)

The docker compose file has a dependency on an authentication and signing server. 
The server provides an intermediary to the Keycloak OAuth2 authentication and also acts as a signing service, where 
a client application or app can generate and provide a certificate for signing that the service will
verify to ensure it is the same identity as the authenticated user/client and return a signed identity certificate.
The client can use this certificate to sign FHIR resources to enable an auditor to verify their provenance.  

The [Keycloak Auth server](https://bitbucket.org/ihlchip/keycloak-auth-bch) is built into the local docker repository 
by the docker-compose.yaml script.

The docker-compose file also executes a transient job that provisions a test realm and user for client testing. 

Client interactions with the 21CFR11-compliant server are demonstrated with a command-line client available from this location: 
[fhir-21cfr11pro-client-example](https://bitbucket.org/ihlchip/fhir-21cfr11pro-client-example]   

## Context

The below diagram shows the context of the entire system and how the docker containers interact
in the [docker-compose.yaml](docker-compose.yaml) file. 

![HAPI FHIR JPA Server with OAuth and 21CFR11 Provenance and Journaling](images/21cfr11-containers-context.png)

## Technology

- Java 11
- Maven for Java dependency management
- Jetty
- Spring Framework
- [Keycloak](https://www.keycloak.org) OpenID Connect and OAuth2
- [HAPI](http://hapifhir.io/) Java FHIR Client and Server templates and frameworks
- [ImmuDB](https://immudb.io)

## How to deploy the FHIR server standalone

Compile and package the project with:

```
mvn clean install
```

and run locally with:

```
mvn jetty:run
```

Go to your browser and type http://localhost:8080/hapi. If you enable the OAuth capability (see next section) deploy the [Keycloak OAuth 2.0](https://github.com/AriHealth/keycloak-auth) and dependencies.

## Environment variables

The following environment variables must be set prior to the execution:

* `DB_VENDOR`: Specify vendor. The list of possible vendors are [`DERBY`, `MYSQL`, `MARIADB`, `POSTGRESQL`] (`optional`, default value `DERBY`)
* `DB_HOST`: Host where the database is deployed (it can be empty for `DERBY`) (`optional`, default value `localhost`)
* `DB_PORT`: Port where the database is deployed (it can be empty for `DERBY`) (`optional`, default value `3306` for `MYSQL`, `5432` for `POSTGRESQL).
* `DB_USER`: FHIR user for the database (it can be empty for `DERBY`) (`optional`, default value `fhiruser`). 
* `DB_PASSWORD`: Password for `DB_USER` in the database (it can be empty for `DERBY`) (`optional`, default value `fhirpwd`).
* `DB_DATABASE`: DB for the database (it can be empty for `DERBY`) (`optional`, default value `fhirdb`).
* `LUCENE_FOLDER`: Place where the Lucene index are stored (`optional`, default value `/var/lib/tomcat8/webapps/hapi/indexes`).
* `OAUTH_ENABLE`: To enable/disable authentication (`optional`, default value `false`).
* `OAUTH_URL`: In case of enabling authentication, the url where the Keycloak OAuth 2.0 is available (`optional`, default value `http://auth:8081`).
* `LOGGING_FOLDER`: To configure the folder for the logs to be managed by ELK (`optional`, default value `logs`)



## Database

This image supports `Derby`, `MySQL`, `MariaDB` and `PostgreSQL`. To configure the database use `DB_VENDOR` with the following values [`DERBY`, `MYSQL`, `MARIADB`, `POSTGRESQL`].

### Derby

No container is needed.

### MySQL
```
	docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=rootpwd -e MYSQL_USER=fhiruser -e MYSQL_PASSWORD=fhirpwd MYSQL_DATABASE=fhirdb mysql:5.7
```

### MariaDB
```
	docker run -d --name mariadb -e MYSQL_ROOT_PASSWORD=rootpwd -e MYSQL_USER=fhiruser -e MYSQL_PASSWORD=fhirpwd MYSQL_DATABASE=fhirdb mariadb/server:10.3
```

### PostgreSQL
```
	docker run -d --name postgres -e POSTGRES_USER=fhiruser -e POSTGRES_PASSWORD=fhirpwd POSTGRES_DB=fhirdb postgres
```

## Docker deployment

### Full deployment with Docker Compose

The project comes with a [docker-compose](https://docs.docker.com/compose/) file which deploys testing containers for Keycloak (port: 9090), HAPI (8080), OAuth 2.0 Authenticator (8081):

0. Check the configuration values at the environment (`.env`) file. Put `OAUTH_ENABLE`=`true` to protect the API. You can modify at your needs.
1. Execute `docker-compose up -d`
2. Wait a couple of minutes until the stack is deployed. Check the logs with `docker logs --details hapi-fhir`
3. Access the Keycloak console `http://localhost:9090` (**user**: admin, **password**: Pa55w0rd)
      * Create a realm `HAPIFHIR`
      * Inside the realm create a client_id: `hapifhir-client`
      * Create a user: test. Modify the credentials (temporary off)
4. Access the authenticator `http://localhost:8081/swagger-ui.html`
      * Open Login operation under Auth controller, `Try it out`:
      * In the JSON include the user created in Keycloak (`test`)
      * The response is an OAuth access token
      * Copy the access token
      * Last thing is to configure the [HAPI client including the authorization token in the header](http://hapifhir.io/doc_rest_client_interceptor.html). Authorization header: `Bearer <access token>` (see the java snippet below)
```
		BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(token); 
		// Create a client and post the transaction to the server
		IGenericClient client = ctx.newRestfulGenericClient(FHIR_URL);
		// Register the interceptor with your client (either style)
		client.registerInterceptor(authInterceptor);
```
5. Check that the resource has been created in HAPI `http://localhost:8080`

### Simple deployment

Build the image:
```
	docker build -t hapi-fhir/hapi-fhir-cdr .
```

It is supposed [auth](https://hub.docker.com/r/ccavero/keycloak-auth) is deployed. Use this command to start the container (take into account the links to auth and database containers).  For instance if MariaDB is selected the docker configuration is as follows: 
```
	docker run -d --name hapi-fhir-cdr -p 8080:8080 arihealth/hapi-fhir-cdr-oauth -e DB_VENDOR=MARIADB -e DB_HOST=mariadb -e DB_PORT=3306 -e DB_USER=fhiruser -e DB_PASSWORD=fhirpwd DB_DATABASE=fhirdb -e LUCENE_FOLDER=XXX OAUTH_ENABLE=true OAUTH_URL=http://auth:8081/ --link auth:auth --link mariadb:mariadb 
```
Note: with this command data is persisted across container restarts, but not after removal of the container. Use a new container for the database with a shared docker volume.

## MySQL configuration

We follow the recommended [MySQL configuration](https://groups.google.com/forum/#!topic/hapi-fhir/ValHrT3hAj0) including extra jpaProperties to [avoid permission problems with Lucene indexes](https://groups.google.com/forum/#!topic/hapi-fhir/wyh4TEpUuSA) of the default configuration:

1. Dependency in the pom:
```
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>6.0.5</version>
    </dependency>
```
2. In FhirServerConfig

* In the data source
  
```
    public DataSource dataSource() {
      BasicDataSource retVal = new BasicDataSource();
        try {
            retVal.setDriver(new com.mysql.cj.jdbc.Driver());
        } catch (SQLException e) {
        
```

* In the JPA properties

```
    private Properties jpaProperties() {

      Properties extraProperties = new Properties();

      // Use MySQL hibernate
      extraProperties.put("hibernate.dialect", "org.hibernate.dialect.MySQL5InnoDBDialect");
	  
      // To avoid problems with Lucene indexes permissions
      extraProperties.put("hibernate.search.default.indexBase", "/var/lib/tomcat8/webapps/hapi-fhir-jpaserver-example-mysql-oauth/indexes");
    }
```

## OAuth2 authorization

We use as IdM [KeyCloak](http://www.keycloak.org/). [OAuth2 authorization in HAPI](http://hapifhir.io/doc_rest_server_security.html#Authorization_Interceptor) is done [via Interceptors](http://hapifhir.io/doc_rest_server_interceptor.html). We reuse the [careconnect implementation](https://github.com/nhsconnect/careconnect-reference-implementation/blob/master/ccri-fhirserver/src/main/java/uk/nhs/careconnect/ccri/fhirserver/oauth2/OAuthTokenUtil.java
) creating a new IServerInterceptor in FhirConfig that is automatically registered when launching the server:

```
    @Bean(autowire = Autowire.BY_TYPE)
    public IServerInterceptor subscriptionKeyCloakInterceptor() {
       KeyCloakInterceptor retVal = new KeyCloakInterceptor();
       return retVal;
    }
```


```
    BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(token);

    // Create a client and post the transaction to the server
    IGenericClient client = ctx.newRestfulGenericClient(FHIR_URL);
    // Register the interceptor with your client (either style)
    client.registerInterceptor(authInterceptor);
```



## Using the kcadm.sh to provision initialisation information 

_This is handled by the provisioning container in `docker-compose.yaml`_  

`bin/kcadm.sh config credentials --server http://localhost:9090/auth --realm master --user admin --client admin-cli --password Pa55w0rd`

`bin/kcadm.sh create realms -s realm=HAPIFHIR -s enabled=true`

`bin/kcadm.sh create clients -r HAPIFHIR -s clientId=hapifhir-client -s enabled=true -s directAccessGrantsEnabled=true -s publicClient=true`

`bin/kcadm.sh create users -r HAPIFHIR -s username=test -s enabled=true`

`bin/kcadm.sh set-password -r HAPIFHIR --username test --new-password Pa55w0rd`


# Immutable DB (immudb)

Using immudb we can store key/value pairs in an immutable, tamper-evident database. 

The `immudb` docker container, when started, creates a volume with the password pre-set for demonstration purposes. 
Be sure to use the other options or run immudb isolated on the same networking host to provide good security for 
production deployments.

## License

Apache 2.0

By downloading this software, the downloader agrees with the specified terms and conditions of the License Agreement 
and the particularities of the license provided.    

# Future Enhancements and Production Deployment

The example code has been preparet ot be a clear and clean example of a system that implements the [21 CFR Part 11 
Patient Reported Outcomes Implementation Guide](https://chb.github.io/21cfr11pro-ig/). 

## Securing the Journal

In this example implementation the path from the FHIR server to ImmuDB is HTTP and secured with a 
non-secret password. For production deployment use a secret password, use a TLS proxy like Nginx, or retain the 
network isolation that keeps ImmuDB & ImmuGW and the FHIR Server's communication private. 

ImmuDB also offers an ability to sign data with a public key cryptography, though the REST API does not support this yet. 
If a GRPC API is used then the server can run key refresh operations and provide an additional level of assurance.  

## Journal Alternatives

ImmuDB is a fast, self-deployed alternative to Aamzon Web Services' Quantum Ledger Database (QLDB).  The function ImmuGW and 
ImmuDB fulfil in the example implementation can be played by QLDB. 

For an AWS-based deployment Amazon RDS could be used as a HAPI FHIR pseristence store, AWS Cognito instead of Keycloak, 
and QLDB instead of ImmuDB. 

![Simple AWS 21CFR11 FHIR architecture](images/21cfr11-aws-qldb-example.png)   

## Request Authorization

The example implementation assumes that all requests are benign requests by a single client. A production FHIR server 
should implement authorization that restricts clients to adding resources with only their own client's matched patient 
ID. For clinical reasons the app interface should not support `GET` or `DELETE` operations and should be cautious about 
allowing  `PATCH` `PUT` and other non-`POST` operations. 

## More Advanced Interceptors 

The [JournalInterceptor](net/atos/ari/cdr/starter/journalinterceptor/JournalInterceptor.java) overriding the 
`outgoingResponse()` method relies on the client behaving according to the implementation guide so that requests that
add or otherwise modify FHIR resources return a copy of the modified resource. 

Ideally the FHIR server would reject any client request that did not have the representation header set, or 
it would ignore return value settings and always provide the server's latest copy of the resource in any successful 
mutation operation (`PUT`, `POST`, `PATCH`, `DELETE`).
    