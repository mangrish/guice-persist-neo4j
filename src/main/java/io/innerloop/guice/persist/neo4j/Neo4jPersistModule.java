package io.innerloop.guice.persist.neo4j;

import com.google.inject.Singleton;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.util.Providers;
import org.aopalliance.intercept.MethodInterceptor;
import org.neo4j.ogm.session.Session;

import java.util.Properties;

/**
 * Created by markangrish on 11/04/2016.
 */
public class Neo4jPersistModule extends PersistModule
{
    private final String[] packages;

    private MethodInterceptor transactionInterceptor;

    private Properties properties;

    public Neo4jPersistModule(String... packages)
    {
        this.packages = packages;
    }

    @Override
    protected void configurePersistence()
    {
        bind(String[].class).annotatedWith(Neo4j.class).toInstance(packages);

        if (null != properties)
        {
            bind(Properties.class).annotatedWith(Neo4j.class).toInstance(properties);
        }
        else
        {
            bind(Properties.class).annotatedWith(Neo4j.class).toProvider(Providers.of(null));
        }

        bind(Neo4jPersistService.class).in(Singleton.class);
        bind(PersistService.class).to(Neo4jPersistService.class);
        bind(UnitOfWork.class).to(Neo4jPersistService.class);
        bind(Session.class).toProvider(Neo4jPersistService.class);

        transactionInterceptor = new Neo4jLocalTxnInterceptor();
        requestInjection(transactionInterceptor);
    }

    @Override
    protected MethodInterceptor getTransactionInterceptor()
    {
        return this.transactionInterceptor;
    }

    public Neo4jPersistModule properties(Properties properties)
    {
        this.properties = properties;
        return this;
    }
}
