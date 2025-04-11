package org.linlinjava.litemall.library.command;

import org.linlinjava.litemall.library.Command;
import org.linlinjava.litemall.library.Event;
import org.linlinjava.litemall.library.State;

import java.util.HashMap;
import java.util.Map;

public final class CommandHandlerBuilder <C extends Command, E extends Event, S extends State>{

    private final Map<Class<? extends C>, CmdHandler<? extends C, S, E>> handlers = new HashMap<>();



    public <T extends C> CommandHandlerBuilder<C,E, S> onCommand(Class<T> commandClass, CmdHandler<T, S, E> handler) {
        handlers.put(commandClass, handler);
        return this;
    }

    public CmdHandler<C, S, E> build() {
        return (cmd, state) -> {
            @SuppressWarnings("unchecked")
            CmdHandler<C, S, E> handler = (CmdHandler<C, S, E>) handlers.get(cmd.getClass());
            if (handler == null) {
                throw new IllegalArgumentException("No handler registered for command class: " + cmd.getClass());
            }
            return handler.applyCommand(cmd, state);
        };
    }


    public Map<Class<? extends C>, CmdHandler<? extends C, S, E>> getCmdHandlers() {
        return handlers;
    }

}
