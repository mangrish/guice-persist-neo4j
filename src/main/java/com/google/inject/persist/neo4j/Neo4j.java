package com.google.inject.persist.neo4j;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.google.inject.BindingAnnotation;

/**
 * A binding annotation for internal Neo4j module properties.
 *
 * @author mark.angrish@gmail.com (Mark Angrish)
 */
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation @interface Neo4j {}
