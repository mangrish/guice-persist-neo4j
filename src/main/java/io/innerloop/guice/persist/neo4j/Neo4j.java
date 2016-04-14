package io.innerloop.guice.persist.neo4j;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A binding annotation for internal Neo4j module properties.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation @interface Neo4j
{}
