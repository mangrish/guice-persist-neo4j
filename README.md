# guice-persist-neo4j


[Guice Persist](https://github.com/google/guice/wiki/GuicePersist) Support for [Neo4j OGM](https://github.com/neo4j/neo4j-ogm).

[![Build Status](https://travis-ci.org/inner-loop/guice-persist-neo4j.svg?branch=master)](https://travis-ci.org/inner-loop/guice-persist-neo4j)


# Quick Start

This module requires:

- Java 8+
- Neo4j OGM 2.0.4+
- Google Guice Persist 4.1.0+

## Install from Maven

Add the following to your ```<dependencies> .. </dependencies>``` section.

```maven
<dependency>
    <groupId>io.innerloop</groupId>
    <artifactId>guice-persist-neo4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Install from Gradle

Add the following to your ```dependencies { .. }``` section.

```gradle
compile group: 'io.innerloop', name: 'guice-persist-neo4j', version: '0.1.0'
```

... or more simply:

```gradle
compile: 'io.innerloop:guice-persist-neo4j:0.1.0'
```

# Usage

*Note: Please make sure you have familiarised yourself with the [Guice Persist documentation]([Guice Persist](https://github.com/google/guice/wiki/GuicePersist)).*

Neo4j Object to Graph Mapping (OGM) library provides optimised mapping support between Java and installations of Neo4j utilising the Cypher query language acros a variety of protocol. 
It is roughly an equivalent to JPA or Hibernate. Guice Persist Neo4j extends Guice Persist to provide transactional support for your Guice applications.

##Enabling Persistence Support

To enable persistence support, simply install the Neo4j module:

```java
Injector injector = Guice.createInjector(..., new Neo4jPersistModule("com.package.domain"));
```

where `com.package.domain` is a list of domains you want the OGM to manage.


In the OGM, you specify your configuration in an `ogm.properties` file at the root of the classpath. Here is an example of a simple OGM configuration for connecting with the bolt protocol:

```properties
driver=org.neo4j.ogm.drivers.bolt.driver.BoltDriver
URI=bolt://user:password@localhost
```

You may also configure the application directly on the module itself:

```java
Names.bindProperties(binder(), System.getProperties());
Injector injector = Guice.createInjector(..., new Neo4jPersistModule("com.package.domain").properties(getPersistenceProperties()));

...

private static Properties getPersistenceProperties()
{
    Properties properties = new Properties();
    properties.put("neo4j.ogm.driver", System.getProperty("neo4j.ogm.driver"));
    properties.put("neo4j.ogm.url", System.getProperty("neo4j.ogm.url"));
    properties.put("neo4j.ogm.username", System.getProperty("neo4j.ogm.username"));
    properties.put("neo4j.ogm.password", System.getProperty("neo4j.ogm.password"));

    return properties;
}
```

Note that the way properties are configured is being reviewed and will probably be changed in an upcoming release.


Finally, you must decide when the persistence service is to be started by invoking start() on PersistService. A simple initializer class to trigger when to start is recommended:

```java
public class MyInitializer { 
    @Inject MyInitializer(PersistService service) {
        service.start(); 

        // At this point Neo4j OGM is started and ready.
    } 
}
```

It makes good sense to use Guice's Service API to start all services in your application at once. In the case of web applications, this is done for you by installing the PersistFilter (see below).

##Using the OGM Session

Once you have the injector created, you can freely inject and use an EntityManager in your transactional services:

```
import com.google.inject.persist.Transactional;
import org.neo4j.ogm.session.Session; 

public class MyService {
    @Inject Session session; 

    @Transactional 
    public void createNewPerson() {
        session.save(new Person(...)); 
    } 
}
```

This is known as the session-per-transaction strategy. For more information on transactions and units of work (sessions), see [this page](https://github.com/google/guice/wiki/Transactions).

*Note that if you make `MyService` a `@Singleton`, then you should inject `Provider<Session>` instead. This is the pattern you would use when building things like Repositories.*

## Web Environments (session-per-http-request)

So far, we've seen the session-per-transaction strategy. In web environments this is atypical, and generally a session-per-http-request is preferred (sometimes also called open-session-in-view). 
To enable this strategy, you first need to add a filter to your ServletModule:

```java
public class MyModule extends ServletModule {
  protected void configureServlets() {
    install(new Neo4jPersistModule("com.example.domain"));  // like we saw earlier.

    filter("/*").through(PersistFilter.class);
  }
}
```

You should typically install this filter before any others that require the Session to do their work.

Note that with this configuration you can run multiple transactions within the same Session (i.e. the same HTTP request).
