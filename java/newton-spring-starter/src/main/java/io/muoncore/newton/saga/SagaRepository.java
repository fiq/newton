package io.muoncore.newton.saga;

import io.muoncore.newton.NewtonEvent;
import io.muoncore.newton.DocumentId;

import java.util.List;
import java.util.Optional;

public interface SagaRepository {
    <T extends Saga> Optional<T> load(DocumentId sagaIdentifier, Class<T> type);
    void save(Saga saga);

    List<SagaInterest> getSagasInterestedIn(Class<? extends NewtonEvent> eventClass);

}
