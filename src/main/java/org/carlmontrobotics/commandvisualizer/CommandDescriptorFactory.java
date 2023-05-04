package org.carlmontrobotics.commandvisualizer;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

public class CommandDescriptorFactory {

    public static final Map<Class<? extends Command>, CommandDescriber<? extends Command>> describers = new HashMap<>();
    public static final Map<Command, CommandDescriptor> descriptors = new WeakHashMap<>();

    public static <T extends Command> void registerDescriber(Class<T> clazz, CommandDescriber<T> describer) {
        describers.put(clazz, describer);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static CommandDescriptor fromCommand(Command command, boolean isRunning) throws WrapperException {
        try {
            CommandDescriptor descriptor = descriptors.computeIfAbsent(command, cmd -> {
                CommandDescriptor newDescriptor = new CommandDescriptor();
                newDescriptor.id = System.identityHashCode(command);
                while(descriptors.values().stream().anyMatch(d -> d.id == newDescriptor.id))
                    newDescriptor.id++;
                return newDescriptor;
            });

            descriptor.name = command.getName();
            descriptor.clazz = command.getClass().getName();
            descriptor.isRunning = isRunning;
            descriptor.runsWhenDisabled = command.runsWhenDisabled();
            descriptor.isComposed = CommandScheduler.getInstance().isComposed(command);
            descriptor.interruptionBehavior = command.getInterruptionBehavior();
            descriptor.subCommands = new CommandDescriptor[0];
            descriptor.requirements = command.getRequirements().stream().map(Object::getClass)
                    .map(clazz -> clazz.getName()).toArray(String[]::new);

            if(command instanceof Describable) {
                descriptor.describer = command.getClass().getName();
                ((Describable) command).describe(descriptor, isRunning);
            } else {
                Class<? extends Command> clazz = command.getClass();

                for(;;) { // Walk up the class hierarchy
                    if(describers.containsKey(clazz)) { // This could be optimized
                        // Cast away the generic type
                        CommandDescriber describer = (CommandDescriber) describers.get(clazz);
                        descriptor.describer = describer.getClass().getName();
                        describer.describe(descriptor, command, isRunning);
                        break;
                    }

                    if(Command.class.isAssignableFrom(clazz.getSuperclass()))
                        clazz = clazz.getSuperclass().asSubclass(Command.class);
                    else break;
                }
            }

            return descriptor;
        } catch(Exception e) {
            throw e instanceof WrapperException ? (WrapperException) e : new WrapperException(e);
        }
    }

}
