package com.google.inject.persist.neo4j;

import static com.google.inject.matcher.Matchers.*;

import java.lang.reflect.Method;
import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.util.Providers;
import org.aopalliance.intercept.MethodInterceptor;
import org.neo4j.ogm.session.Session;

/**
 * @author mark.angrish@gmail.com (Mark Angrish)
 */
public class Neo4jPersistModule extends AbstractModule {

	private static final class TransactionalMethodMatcher extends AbstractMatcher<Method> {

		@Override
		public boolean matches(final Method method) {
			return method.isAnnotationPresent(Transactional.class) && !method.isSynthetic();
		}
	}

	private static final class TransactionalClassMethodMatcher extends AbstractMatcher<Method> {

		@Override
		public boolean matches(final Method method) {
			return !method.isSynthetic();
		}
	}

	private final String[] packages;

	private MethodInterceptor transactionInterceptor;

	private Properties properties;

	public Neo4jPersistModule(String... packages) {
		this.packages = packages;
	}

	@Override
	protected final void configure() {
		configurePersistence();

		requireBinding(PersistService.class);
		requireBinding(UnitOfWork.class);
	/*if[AOP]*/
		// wrapping in an if[AOP] just to allow this to compile in NO_AOP -- it won't be used

		// class-level @Transacational
		bindInterceptor(annotatedWith(Transactional.class),
				new TransactionalClassMethodMatcher(),
				getTransactionInterceptor());
		// method-level @Transacational
		bindInterceptor(any(), new TransactionalMethodMatcher(), getTransactionInterceptor());
    /*end[AOP]*/
	}


	protected void configurePersistence() {
		bind(String[].class).annotatedWith(Neo4j.class).toInstance(packages);

		if (null != properties) {
			bind(Properties.class).annotatedWith(Neo4j.class).toInstance(properties);
		} else {
			bind(Properties.class).annotatedWith(Neo4j.class).toProvider(Providers.of(null));
		}

		bind(Neo4jPersistService.class).in(Singleton.class);
		bind(PersistService.class).to(Neo4jPersistService.class);
		bind(UnitOfWork.class).to(Neo4jPersistService.class);
		bind(Session.class).toProvider(Neo4jPersistService.class);

		transactionInterceptor = new Neo4jLocalTxnInterceptor();
		requestInjection(transactionInterceptor);
	}

	protected MethodInterceptor getTransactionInterceptor() {
		return this.transactionInterceptor;
	}

	public Neo4jPersistModule properties(Properties properties) {
		this.properties = properties;
		return this;
	}
}
