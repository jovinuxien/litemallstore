package org.linlinjava.litemall.library.command;

import org.linlinjava.litemall.library.Command;
import org.linlinjava.litemall.library.Event;
import org.linlinjava.litemall.library.State;
import org.linlinjava.litemall.library.effect.Effect;


@FunctionalInterface
public interface CmdHandler<C extends Command, S extends State, E extends Event> {
    Effect<E, S> applyCommand(C cmd, S state);
}
