package io.innerloop.guice.persist.neo4j;

import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.transaction.Transaction;

import javax.inject.Inject;
import java.lang.reflect.Method;

/**
 * Created by markangrish on 11/04/2016.
 */
class Neo4jLocalTxnInterceptor implements MethodInterceptor
{
    @Inject
    private Neo4jPersistService sessionProvider;

    @Inject
    private UnitOfWork unitOfWork;

    @Transactional
    private static class Internal
    {
    }

    // Tracks if the unit of work was begun implicitly by this transaction.
    private final ThreadLocal<Boolean> workStarted = new ThreadLocal<>();

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable
    {
        // Should we start a unit of work?
        if (!sessionProvider.isWorking())
        {
            sessionProvider.begin();
            workStarted.set(true);
        }

        Transactional transactional = readTransactionMetadata(invocation);
        Session session = this.sessionProvider.get();

        // Allow 'joining' of transactions if there is an enclosing @Transactional method.
        Transaction txn = session.getTransaction();

        if (txn != null)
        {
            if (txn.status().equals(Transaction.Status.OPEN) || txn.status().equals(Transaction.Status.PENDING))
            {
                return invocation.proceed();
            }
        }

        txn = session.beginTransaction();

        Object result;
        try
        {
            result = invocation.proceed();

        }
        catch (Exception e)
        {
            //commit transaction only if rollback didnt occur
            if (rollbackIfNecessary(transactional, e, txn))
            {
                txn.commit();
            }

            //propagate whatever exception is thrown anyway
            throw e;
        }
        finally
        {
            // Close the session if necessary (guarded so this code doesn't run unless catch fired).
            if (null != workStarted.get() &&
                (txn.status().equals(Transaction.Status.CLOSED) || txn.status().equals(Transaction.Status.COMMITTED) ||
                 txn.status().equals(Transaction.Status.ROLLEDBACK)))
            {
                workStarted.remove();
                unitOfWork.end();
            }
        }

        //everything was normal so commit the txn (do not move into try block above as it
        //  interferes with the advised method's throwing semantics)
        try
        {
            txn.commit();
        }
        finally
        {
            //close the session if necessary
            if (null != workStarted.get())
            {
                workStarted.remove();
                unitOfWork.end();
            }
        }

        //or return result
        return result;
    }

    // TODO(dhanji): Cache this method's results.
    private Transactional readTransactionMetadata(MethodInvocation methodInvocation)
    {
        Transactional transactional;
        Method method = methodInvocation.getMethod();
        Class<?> targetClass = methodInvocation.getThis().getClass();

        transactional = method.getAnnotation(Transactional.class);
        if (null == transactional)
        {
            // If none on method, try the class.
            transactional = targetClass.getAnnotation(Transactional.class);
        }
        if (null == transactional)
        {
            // If there is no transactional annotation present, use the default
            transactional = Neo4jLocalTxnInterceptor.Internal.class.getAnnotation(Transactional.class);
        }

        return transactional;
    }

    /**
     * Returns True if rollback DID NOT HAPPEN (i.e. if commit should continue).
     *
     * @param transactional
     *         The metadata annotaiton of the method
     * @param e
     *         The exception to test for rollback
     * @param txn
     *         A JPA Transaction to issue rollbacks on
     */
    private boolean rollbackIfNecessary(Transactional transactional, Exception e, Transaction txn)
    {
        boolean commit = true;

        //check rollback clauses
        for (Class<? extends Exception> rollBackOn : transactional.rollbackOn())
        {

            //if one matched, try to perform a rollback
            if (rollBackOn.isInstance(e))
            {
                commit = false;

                //check ignore clauses (supercedes rollback clause)
                for (Class<? extends Exception> exceptOn : transactional.ignore())
                {
                    //An exception to the rollback clause was found, DON'T rollback
                    // (i.e. commit and throw anyway)
                    if (exceptOn.isInstance(e))
                    {
                        commit = true;
                        break;
                    }
                }

                //rollback only if nothing matched the ignore check
                if (!commit)
                {
                    txn.rollback();
                }
                //otherwise continue to commit

                break;
            }
        }

        return commit;
    }
}
