package com.google.inject.persist.neo4j;

import javax.inject.Inject;
import java.util.Properties;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import org.neo4j.ogm.authentication.UsernamePasswordCredentials;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

/**
 * @author mark.angrish@gmail.com (Mark Angrish)
 */
@Singleton
class Neo4jPersistService implements Provider<Session>, UnitOfWork, PersistService
{
    private final ThreadLocal<Session> sessions = new ThreadLocal<>();

    private final String[] persistencePackages;

    private final Configuration configuration;

    private volatile SessionFactory sessionFactory;


    @Inject
    Neo4jPersistService(@Neo4j String[] persistencePackages, @Neo4j Properties persistenceProperties)
    {
        this.persistencePackages = persistencePackages;
        configuration = new Configuration();
        configuration.driverConfiguration()
                .setDriverClassName(persistenceProperties.getProperty("neo4j.ogm.driver"))
                .setURI(persistenceProperties.getProperty("neo4j.ogm.url"))
                .setCredentials(new UsernamePasswordCredentials(persistenceProperties.getProperty("neo4j.ogm.username"),
                                                                persistenceProperties.getProperty("neo4j.ogm.password")));
    }

    @Override
    public Session get()
    {
        if (!isWorking())
        {
            begin();
        }

        Session session = sessions.get();

        if (session == null)
        {
            throw new IllegalStateException("Requested Session outside work unit. " +
                                            "Try calling UnitOfWork.begin() first, or use a PersistFilter if you " +
                                            "are inside a servlet environment.");
        }

        return session;
    }

    boolean isWorking()
    {
        return sessions.get() != null;
    }


    @Override
    public synchronized void start()
    {
        if (sessionFactory != null)
        {
            throw new IllegalStateException("Persistence service was already initialized.");
        }

        this.sessionFactory = new SessionFactory(configuration, persistencePackages);
    }

    @Override
    public synchronized void stop()
    {
        // Do nothing...
    }


    @Override
    public void begin()
    {
        if (sessions.get() != null)
        {
            throw new IllegalStateException("Work already begun on this thread. Looks like you have called UnitOfWork.begin() twice" +
                                            " without a balancing call to end() in between.");
        }
        sessions.set(sessionFactory.openSession());
    }

    @Override
    public void end()
    {
        Session session = sessions.get();

        // Let's not penalize users for calling end() multiple times.
        if (session == null)
        {
            return;
        }

        sessions.remove();
    }
}
