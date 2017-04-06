package io.muoncore.newton.eventsource;

import io.muoncore.newton.DocumentId;

import java.util.concurrent.Callable;

public interface EventSourceRepository<A> {

	A load(DocumentId aggregateIdentifier) throws AggregateNotFoundException;

	A load(DocumentId aggregateIdentifier, Long expectedVersion) throws AggregateNotFoundException, OptimisticLockException;

	A newInstance(Callable<A> factoryMethod);

	void save(A aggregate);

}
