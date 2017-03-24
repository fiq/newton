package io.muoncore.newton;


import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class UniqueAggregateDomainService<V> {

	private Map<NewtonIdentifier, V> entriesMap = Collections.synchronizedMap(new HashMap<>());

	private StreamSubscriptionManager streamSubscriptionManager;
	private Class<? extends AggregateRoot> aggregateType;
	private DynamicInvokeEventAdaptor eventAdaptor = new DynamicInvokeEventAdaptor(this, OnViewEvent.class);

	public UniqueAggregateDomainService(StreamSubscriptionManager streamSubscriptionManager, Class<? extends AggregateRoot> aggregateType) throws IOException {
		this.streamSubscriptionManager = streamSubscriptionManager;
		this.aggregateType = aggregateType;
	}

	private void handleEvent(NewtonEvent event) {
		eventAdaptor.accept(event);
	}

	@PostConstruct
	public void initSubscription() throws InterruptedException {
		streamSubscriptionManager.localNonTrackingSubscription(aggregateType.getSimpleName(), this::handleEvent);
	}

	public boolean isUnique(NewtonIdentifier thisId, V value) {
		return !exists(thisId, value);
	}

	public boolean exists(NewtonIdentifier thisId, V value) {
		if (thisId != null) {
			return entriesMap.entrySet().stream().anyMatch(x -> x.getValue().equals(value) && !x.getKey().equals(thisId));
		}
		return entriesMap.values().stream().anyMatch(v -> v.equals(value));
	}

	public void addValue(NewtonIdentifier id, V value) {
		entriesMap.put(id, value);
	}

	public void removeValue(NewtonIdentifier id) {
		entriesMap.remove(id);
	}

	public void updateValue(NewtonIdentifier id, V value) {
		entriesMap.entrySet().stream()
			.filter(entry -> entry.getKey().equals(id))
			.findFirst()
			.ifPresent(entry -> entry.setValue(value));
	}

	protected Optional<V> find(Predicate<V> predicate) {
		return entriesMap.values().stream().filter(predicate).findFirst();
	}


}