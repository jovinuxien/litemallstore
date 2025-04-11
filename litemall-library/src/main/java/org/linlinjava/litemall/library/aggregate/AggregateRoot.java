package org.linlinjava.litemall.library.aggregate;

import org.linlinjava.litemall.library.Command;
import org.linlinjava.litemall.library.Event;
import org.linlinjava.litemall.library.State;
import org.linlinjava.litemall.library.command.CmdHandler;
import org.linlinjava.litemall.library.command.CommandHandlerBuilder;
import org.linlinjava.litemall.library.eventsource.EventHandler;
import org.linlinjava.litemall.library.eventsource.EventHandlerBuilder;
import org.linlinjava.litemall.library.exceptions.InvalidEventException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Data
@NoArgsConstructor
public abstract class AggregateRoot<C extends Command, E extends Event, S extends State> {

    protected String id;
    protected String type;
    protected long version;
    protected final List<E> changes = new ArrayList<>();
    protected String persistentID = "empty persist id";



    protected final CommandHandlerBuilder<C, E, S> newCommandHandlerBuilder() {
        return new CommandHandlerBuilder<C, E, S>();
    }
    public final EventHandlerBuilder<E, S> newEventHandlerBuilder(){
        return new EventHandlerBuilder<>();
    }

    /**
     * Retrieves a map of event handlers associated with the aggregate root.
     * The map keys are the event classes and the values are the corresponding event handlers.
     *
     * @return A map of event handlers, where the keys are event classes and the values are event handlers.
     */
    private Map<Class<? extends E>, EventHandler<? extends E, ? extends S>> getEvtHandlers(){
        return new EventHandlerBuilder<E, S>().getEventHandlers();
    }

    /**
     * Retrieves a map of command handlers associated with the aggregate root.
     * The map keys are the command classes and the values are the corresponding command handlers.
     *
     * @return A map of command handlers, where the keys are command classes and the values are command handlers.
     */
    private Map<Class<? extends C>, CmdHandler<? extends C, S, E>> getCmdHandlers(){
        return new CommandHandlerBuilder<C, E, S>().getCmdHandlers();
    }

    public abstract CmdHandler<C, S, E> commandHandler();
    public abstract EventHandler<E, S> eventHandler();
    public abstract S emptyState();


    public AggregateRoot(String aggregateId, String aggregateType) {
        this.id = aggregateId;
        this.type = aggregateType;
    }

    public void load(final List<Event> events) {
        events.forEach(event -> {
            this.validateEvent(event);
            this.raiseEvent(event);
            this.version++;
        });
    }

    public abstract void when(final Event event);

    public void raiseEvent(final Event event) {
        this.validateEvent(event);
        event.setAggregateType(this.type);
        when(event);
        this.version++;
    }

    public void apply(final E event) {
        this.validateEvent(event);
        event.setAggregateType(this.type);
        when(event);
        changes.add(event);
        this.version++;
        event.setVersion(this.version);
    }

    public void clearChanges() {
        this.changes.clear();
    }

    public void toSnapshot() {
        this.clearChanges();
    }

    private void validateEvent(final Event event) {
        if (Objects.isNull(event) || !event.getAggregateId().equals(this.id)) {
            throw new InvalidEventException(event.toString());
        }
    }


//    protected Event createEvent(String eventType, byte[] data, byte[] metadata) {
//        return Event.builder()
//                .aggregateId(this.getId())
//                .version(this.getVersion())
//                .aggregateType(this.getType())
//                .eventType(eventType)
//                .data(Objects.isNull(data) ? new byte[]{} : data)
//                .metaData(Objects.isNull(metadata) ? new byte[]{} : metadata)
//                .timeStamp(LocalDateTime.now())
//               // .build();
//    }

    @Override
    public String toString() {
        return "AggregateRoot{" +
                "  id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", version=" + version +
                ", changes=" + changes +
                '}';
    }




}
