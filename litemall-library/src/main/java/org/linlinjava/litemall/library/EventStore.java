package org.linlinjava.litemall.library;

import org.linlinjava.litemall.library.aggregate.AggregateRoot;
import org.linlinjava.litemall.library.exceptions.AggregateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.*;

import static org.linlinjava.litemall.library.Constants.*;

@Repository
@RequiredArgsConstructor
public class EventStore implements EventStoreDB {

    private static final int SNAPSHOT_FREQUENCY = 3;
    private static final String SAVE_EVENTS_QUERY = "INSERT INTO events (id, aggregate_id, event_type, aggregate_type, version, data, metadata, timestamp) VALUES" +
            " (:aggregate_id, :aggregate_type, :event_type, :version, :data, :metadata, version, now())";
    private static final String LOAD_EVENTS_QUERY = "SELECT id, aggregate_id, event_type, aggregate_type, version, data, meta_data, time_stamp FROM " +
            "events e WHERE e.aggregate_id = :aggregate_id AND e.version > :version ORDER BY e.version ASC";
    private static final String HANDLE_CONCURRENCY_QUERY = "SELECT aggregate_id FROM events e WHERE e.aggregate_id = :aggregate_id LIMIT 1 FOR UPDATE";
    private static final String SAVE_SNAPSHOT_QUERY = "INSERT INTO snapshots (id, aggregate_id, event_type, aggregate_type, version, data, meta_data, time_stamp) " +
            "VALUES (:aggregate_id, :aggregate_type, :event_type, :version, :data, :metadata, version, now()) ON CONFLICT (aggregate_id) DO UPDATE SET data= :data, metadata= :metadata, timestamp= now()";
    private static final String LOAD_SNAPSHOT_QUERY = "SELECT id, aggregate_id, event_type, aggregate_type, version, data, meta_data, time_stamp FROM " +
            " snapshots s WHERE s.aggregate_id = :aggregate_id";
    private static final String EXISTS_QUERY = "SELECT aggregate_id FROM events e WHERE e.aggregate_id = :aggregate_id";


    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(EventStore.class);



    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EventBus eventBus;


    @Override
    //public void saveEvents(@SpanTag("events") List<Event> events) {
    public void saveEvents(List<Event> events) {
        if(events.isEmpty()){
            return;
        }
        final List <Event> changes  = new ArrayList<>(events);
        if(changes.size() > 1){
            this.eventsBatchInsert(changes);
            return;
        }
        final Event event = events.get(0);
        int result = jdbcTemplate.update(SAVE_EVENTS_QUERY, mapFromEvent(event));
        LOG.info("(saveEvents) saved result: {}, event: {}", result, event);
    }

    @Override
    //public List<Event> loadEvents(@SpanTag("aggregateId") String aggregateId, @SpanTag("version") long version) {
    public List<Event> loadEvents( String aggregateId,  long version) {
        return jdbcTemplate.query(LOAD_EVENTS_QUERY, Map.of(AGGREGATE_ID, aggregateId, VERSION, version),
                (rs, rowNum) ->
                     Event.builder()
                            .aggregateId(rs.getString("AGGREGATE_ID"))
                            .eventType(rs.getString("EVENT_TYPE"))
                            .aggregateType(rs.getString("AGGREGATE_TYPE"))
                            .version(rs.getLong("VERSION"))
                            .data(rs.getBytes("DATA"))
                            .metaData(rs.getBytes("METADATA"))
                            .timeStamp(rs.getTimestamp("TIMESTAMP").toLocalDateTime())
                            .build()
        );
    }

    //@NewSpan
    //private Optional<Snapshot> loadSnapshot(@SpanTag("aggregatedId") String aggregateId){
    private Optional<Snapshot> loadSnapshot(String aggregateId){
        return jdbcTemplate.query(LOAD_SNAPSHOT_QUERY, Map.of(AGGREGATE_ID, aggregateId),
                (rs, rowNum) ->
                        Snapshot.builder()
                                .aggregateId(rs.getString("AGGREGATE_ID"))
                                .aggregateType(rs.getString("AGGREGATE_TYPE"))
                                .data(rs.getBytes("DATA"))
                                .metaData(rs.getBytes("METADATA"))
                                .version(rs.getLong("VERSION"))
                                .timeStamp(rs.getTimestamp("TIMESTAMP").toLocalDateTime())
                                .build()).stream().findFirst();
    }

    @Transactional
    //@NewSpan
    @Override
    //public <T extends AggregateRoot> void save(@SpanTag("aggregate") T aggregate) {
    public <T extends AggregateRoot> void save(T aggregate) {
         final List<Event> aggregateEvents = new ArrayList<>(aggregate.getChanges());
         if(aggregate.getVersion() > 1){
             this.handleConcurrency(aggregate.getId());
         }
         this.saveEvents(aggregate.getChanges());
         if(aggregate.getVersion() % SNAPSHOT_FREQUENCY == 0){
             this.saveSnapshot(aggregate);
         }
         eventBus.publish(aggregateEvents);
         LOG.info("(save) saved aggregate {}:", aggregate);
    }

    //@NewSpan
    @Transactional(readOnly = true)
    @Override
    //public <T extends AggregateRoot> T load(@SpanTag("aggregate") String aggregateId, @SpanTag("aggregateType") Class<T> aggregateType) {
    public <T extends AggregateRoot> T load(String aggregateId,  Class<T> aggregateType) {

        final Optional<Snapshot> snapshot = this.loadSnapshot(aggregateId);
        final var aggregate = this.getSnapshotFromClass(snapshot, aggregateId, aggregateType);

        final List<Event> events = this.loadEvents(aggregateId, aggregate.getVersion());
        events.forEach(evt -> {
            aggregate.raiseEvent(evt);
            LOG.info("raise event: {}:", evt.getVersion());
        });
        if(aggregate.getVersion() == 0) throw new AggregateNotFoundException(aggregateId);
        LOG.info("load aggregate: {}", aggregate);
        return aggregate;
    }

    //private <T extends AggregateRoot> void saveSnapshot(@SpanTag("aggregate") T aggregate) {
    private <T extends AggregateRoot> void saveSnapshot(T aggregate) {
        aggregate.toSnapshot();
        final var snapshot = EventSourcingUtils.snapshotFromAggregate(aggregate);

        int updateResult = jdbcTemplate.update
                (SAVE_SNAPSHOT_QUERY,   Map.of(AGGREGATE_ID, aggregate.getId(),
                                        AGGREGATE_TYPE, aggregate.getType(),
                                        VERSION, aggregate.getVersion(),
                                        DATA, Objects.isNull(snapshot.getData())? new byte[]{}: snapshot.getData(),
                                        METADATA, Objects.isNull(snapshot.getMetaData())? new byte[]{} : snapshot.getMetaData())
                );
        LOG.info("{saveSnapshot} updateResult: {}", updateResult);
    }


    //@NewSpan
    @Override
    //public Boolean exists(@SpanTag("aggregateId") String aggregateId) {
    public Boolean exists( String aggregateId) {
            try{
                final var id = jdbcTemplate.queryForObject(EXISTS_QUERY, Map.of(AGGREGATE_ID, aggregateId), Integer.class);
                LOG.info("{aggregate exists id: {}", id);
                return true;
            }catch(Exception ex){
                if(!(ex instanceof EmptyResultDataAccessException)){
                    throw ex;
                }
                return false;
            }
    }

    //@NewSpan
    //private void handleConcurrency(@SpanTag("aggregateId") String aggregateId){
    private void handleConcurrency( String aggregateId){
        try {
            String aggregateID = jdbcTemplate.queryForObject(HANDLE_CONCURRENCY_QUERY, Map.of(AGGREGATE_ID, aggregateId), String.class);
            LOG.info("(handleConcurrency) aggregate for lock: {}", aggregateID);
        } catch(EmptyResultDataAccessException e){
            LOG.info("(handleConcurrency) EmptyResultDataAccessException: {}", e.getMessage());
        }
        LOG.info("(handleConcurrency) aggregateID for lock: {}", aggregateId);
    }

    //@NewSpan
    private void eventsBatchInsert(List<Event> events){
        final var args = events.stream().map(event -> mapFromEvent(event)).toList();
        final Map<String, ?>[] maps = args.toArray(new Map[0]);

        int[] intValue = jdbcTemplate.batchUpdate(SAVE_EVENTS_QUERY, maps);
        LOG.info("(saveEvents) Batched events: {}, event: {}", intValue);
    }

    //@NewSpan
    private Map<String, Serializable> mapFromEvent(Event event){
        return Map.of(
                AGGREGATE_ID, event.getAggregateId(),
                AGGREGATE_TYPE, event.getAggregateType(),
                EVENT_TYPE, event.getEventType(),
                VERSION, event.getVersion(),
                DATA, event.getData(),
                METADATA, event.getMetaData(),
                VERSION, event.getVersion());
    }

    //@NewSpan
    //private <T extends AggregateRoot> T getAggregate(@SpanTag("aggregateId") final String aggregateId, @SpanTag("aggregateType") final Class<T> aggregateType){
    private <T extends AggregateRoot> T getAggregate(final String aggregateId, final Class<T> aggregateType){
        try{
            return aggregateType.getConstructor(String.class).newInstance(aggregateId);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    //@NewSpan
    //private <T extends AggregateRoot> T getSnapshotFromClass(@SpanTag("snapshot")Optional<Snapshot> snapshot, @SpanTag("aggregateId") final String aggregateId, @SpanTag("aggregateType") final Class<T> aggregateType){
    private <T extends AggregateRoot> T getSnapshotFromClass(Optional<Snapshot> snapshot,final String aggregateId, final Class<T> aggregateType){
        if(snapshot.isEmpty()){
            final var defaultSnapshot = EventSourcingUtils.snapshotFromAggregate(getAggregate(aggregateId, aggregateType));
            return EventSourcingUtils.aggregateFromSnapshot(defaultSnapshot, aggregateType);
        }
        return EventSourcingUtils.aggregateFromSnapshot(snapshot.get(), aggregateType);
    }
}
