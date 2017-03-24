package io.muoncore.newton.eventsource.muon;

import io.muoncore.newton.NewtonEvent;
import io.muoncore.newton.NewtonIdentifier;
import io.muoncore.newton.eventsource.AggregateNotFoundException;
import io.muoncore.protocol.event.ClientEvent;
import io.muoncore.protocol.event.client.AggregateEventClient;
import io.muoncore.protocol.event.client.EventClient;
import io.muoncore.newton.AggregateRoot;
import io.muoncore.newton.eventsource.EventSourceRepository;
import io.muoncore.newton.eventsource.OptimisticLockException;
import io.muoncore.newton.utils.muon.MuonLookupUtils;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public abstract class MuonEventSourceRepository<A extends AggregateRoot> implements EventSourceRepository<A> {

	private Class<A> aggregateType;
	private AggregateEventClient aggregateEventClient;
	private EventClient eventClient;
	private final String applicationName;

	public MuonEventSourceRepository(Class<A> type, AggregateEventClient aggregateEventClient, EventClient eventClient, String applicationName) {
		aggregateType = type;
		this.aggregateEventClient = aggregateEventClient;
		this.eventClient = eventClient;
		this.applicationName = applicationName;
	}

	@Override
	public A load(NewtonIdentifier aggregateIdentifier) {
		try {
			A aggregate = aggregateType.newInstance();
			replayEvents(aggregateIdentifier).forEach(aggregate::handleEvent);
			return aggregate;
		} catch (AggregateNotFoundException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Unable to load aggregate: ".concat(aggregateType.getSimpleName()), e);
		}
	}

	@Override
	public A load(NewtonIdentifier aggregateIdentifier, Long version) {
		try {
			A aggregate = (A) aggregateType.newInstance();
			replayEvents(aggregateIdentifier).forEach(aggregate::handleEvent);
			if (aggregate.getVersion() != version) throw new OptimisticLockException(aggregateIdentifier, version, aggregate.getVersion());
			return aggregate;
		} catch (AggregateNotFoundException | OptimisticLockException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Unable to load aggregate: ".concat(aggregateType.getSimpleName()), e);
		}
	}

	@Override
	public A newInstance(Callable<A> factoryMethod) {
		try {
			A result = factoryMethod.call();
			save(result);
			return result;
		} catch (Exception e) {
			throw new IllegalStateException("Unable to create new instance: ".concat(aggregateType.getName()), e);
		}
	}

	@Override
	public void save(A aggregate) {
		emitForAggregatePersistence(aggregate);
		emitForStreamProcessing(aggregate);
	}

	private List<NewtonEvent> replayEvents(NewtonIdentifier id) {
		try {
			//load from event store and obtain the payloads in their concrete types.
			List<NewtonEvent> events = aggregateEventClient.loadAggregateRoot(id.toString())
				.stream()
				.map(event -> event.getPayload(MuonLookupUtils.getDomainClass(event)))
				.collect(Collectors.toList());

			if (events.size() == 0) throw new AggregateNotFoundException(id);

			return events;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void emitForAggregatePersistence(A aggregate) {
		aggregateEventClient.publishDomainEvents(
			aggregate.getId().toString(),
			aggregate.getNewOperations());
	}

	private void emitForStreamProcessing(A aggregate) {
		String streamName = applicationName + "/" + aggregate.getClass().getSimpleName();
		System.out.println("Emitting event on " + streamName);
		aggregate.getNewOperations().forEach(
			event -> eventClient.event(
				ClientEvent
					.ofType(event.getClass().getSimpleName())
					.stream(streamName)
					.payload(event)
					.build()
			));
	}
}