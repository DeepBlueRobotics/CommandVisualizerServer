package org.carlmontrobotics.commandvisualizer;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.ConditionalCommand;
import edu.wpi.first.wpilibj2.command.NotifierCommand;
import edu.wpi.first.wpilibj2.command.ParallelCommandGroup;
import edu.wpi.first.wpilibj2.command.ParallelDeadlineGroup;
import edu.wpi.first.wpilibj2.command.ParallelRaceGroup;
import edu.wpi.first.wpilibj2.command.ProxyCommand;
import edu.wpi.first.wpilibj2.command.ProxyScheduleCommand;
import edu.wpi.first.wpilibj2.command.RepeatCommand;
import edu.wpi.first.wpilibj2.command.ScheduleCommand;
import edu.wpi.first.wpilibj2.command.SelectCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import edu.wpi.first.wpilibj2.command.WaitUntilCommand;
import edu.wpi.first.wpilibj2.command.WrapperCommand;

@SuppressWarnings("unchecked")
public class WPILibCommandDescribers {

    public static final CommandDescriber<ConditionalCommand> conditionalCommandDescriber = (descriptor, command,
            isRunning) -> {
        boolean condition = ((BooleanSupplier) getPrivateField(ConditionalCommand.class, "m_condition")
                .get(command)).getAsBoolean();
        descriptor.parameters.put("condition", condition);
        descriptor.subCommands = new CommandDescriptor[2];
        descriptor.subCommands[0] = CommandDescriptorFactory.fromCommand(
                (Command) getPrivateField(ConditionalCommand.class, "m_onTrue").get(command),
                isRunning && condition);
        descriptor.subCommands[1] = CommandDescriptorFactory.fromCommand(
                (Command) getPrivateField(ConditionalCommand.class, "m_onFalse").get(command),
                isRunning && !condition);
    };

    public static final CommandDescriber<NotifierCommand> notifierCommandDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.parameters.put("period",
                getPrivateField(NotifierCommand.class, "m_period").getDouble(command));
    };

    public static final CommandDescriber<ParallelCommandGroup> parallelCommandGroupDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.subCommands = ((Map<Command, Boolean>) getPrivateField(ParallelCommandGroup.class,
                "m_commands").get(command))
                .entrySet().stream()
                .map(entry -> CommandDescriptorFactory
                        .fromCommand(entry.getKey(), entry.getValue()))
                .toArray(CommandDescriptor[]::new);
    };

    public static final CommandDescriber<ParallelDeadlineGroup> parallelDeadlineGroupDescriber = (descriptor,
            command, isRunning) -> {
        Set<Command> subCommands = ((Map<Command, Boolean>) getPrivateField(ParallelDeadlineGroup.class,
                "m_commands").get(command)).keySet();

        descriptor.subCommands = new CommandDescriptor[subCommands.size()];
        // Ensure deadline is first
        descriptor.subCommands[0] = CommandDescriptorFactory.fromCommand(
                (Command) getPrivateField(ParallelDeadlineGroup.class, "m_deadline").get(command),
                isRunning);
        CommandDescriptor[] additionalSubCommands = ((Map<Command, Boolean>) getPrivateField(
                ParallelDeadlineGroup.class, "m_commands").get(command))
                .entrySet().stream()
                .map(entry -> CommandDescriptorFactory
                        .fromCommand(entry.getKey(), entry.getValue()))
                .filter(Predicate.not(Predicate.isEqual(descriptor.subCommands[0])))
                .toArray(CommandDescriptor[]::new);
        System.arraycopy(additionalSubCommands, 0, descriptor.subCommands, 1, additionalSubCommands.length);
    };

    public static final CommandDescriber<ParallelRaceGroup> parallelRaceGroupDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.subCommands = ((Set<Command>) getPrivateField(ParallelRaceGroup.class, "m_commands")
                .get(command))
                .stream()
                .map(subCommand -> CommandDescriptorFactory
                        .fromCommand(command, isRunning))
                .toArray(CommandDescriptor[]::new);
    };

    public static final CommandDescriber<ProxyCommand> proxyCommandDescriber = (descriptor, command, isRunning) -> {
        if (isRunning)
            descriptor.subCommands = new CommandDescriptor[] {
                    CommandDescriptorFactory.fromCommand(
                            (Command) getPrivateField(ProxyCommand.class, "m_command")
                                    .get(command),
                            isRunning) };
    };

    public static final CommandDescriber<ProxyScheduleCommand> proxyScheduleCommandDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.subCommands = ((Set<Command>) getPrivateField(ParallelRaceGroup.class, "m_toSchedule")
                .get(command))
                .stream()
                .map(subCommand -> CommandDescriptorFactory
                        .fromCommand(command,
                                CommandVisualizer.getRunningCommands()
                                        .contains(subCommand)))
                .toArray(CommandDescriptor[]::new);
    };

    public static final CommandDescriber<RepeatCommand> repeatCommandDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.subCommands = new CommandDescriptor[] {
                CommandDescriptorFactory
                        .fromCommand((Command) getPrivateField(RepeatCommand.class, "m_command")
                                .get(command), isRunning) };
    };

    public static final CommandDescriber<ScheduleCommand> scheduleCommandDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.subCommands = ((Set<Command>) getPrivateField(ParallelRaceGroup.class, "m_toSchedule")
                .get(command))
                .stream()
                .map(subCommand -> CommandDescriptorFactory
                        .fromCommand(command,
                                CommandVisualizer.getRunningCommands()
                                        .contains(subCommand)))
                .toArray(CommandDescriptor[]::new);
    };

    public static final CommandDescriber<SelectCommand> selectCommandDescriber = (descriptor, command,
            isRunning) -> {
        Object currentSelectorValue = ((Supplier<Object>) getPrivateField(SelectCommand.class, "m_selector")
                .get(command)).get();
        descriptor.parameters.put("currentSelectorValue",
                currentSelectorValue == null ? "<null>" : currentSelectorValue.toString());

        Map<Object, Command> subCommands = (Map<Object, Command>) getPrivateField(SelectCommand.class,
                "m_commands")
                .get(command);

        if (subCommands == null) {
            descriptor.parameters.put("hasSupplier", true);
            descriptor.parameters.put("subCommandValues", new String[0]);
            if (isRunning)
                descriptor.subCommands = new CommandDescriptor[] { CommandDescriptorFactory.fromCommand(
                        (Command) getPrivateField(SelectCommand.class, "m_selectedCommand")
                                .get(command),
                        true) };
        } else {
            descriptor.parameters.put("hasSupplier", false);
            String[] subCommandValues = new String[subCommands.size()];
            descriptor.subCommands = new CommandDescriptor[subCommands.size()];

            Map.Entry<Object, Command>[] entries = subCommands.entrySet().toArray(Map.Entry[]::new);

            if (isRunning) {
                descriptor.subCommands[0] = CommandDescriptorFactory.fromCommand(
                        (Command) getPrivateField(SelectCommand.class, "m_selectedCommand")
                                .get(command),
                        true);
                int arrIdx = 1;
                for (int i = 0; i < entries.length; i++) {
                    if (entries[i].getKey() == currentSelectorValue) {
                        subCommandValues[0] = entries[i].getKey().toString();
                        continue;
                    }

                    subCommandValues[arrIdx] = entries[i].getKey().toString();
                    descriptor.subCommands[arrIdx] = CommandDescriptorFactory
                            .fromCommand(entries[i].getValue(), false);
                    arrIdx++;
                }
            } else {
                for (int i = 0; i < entries.length; i++) {
                    subCommandValues[i] = entries[i].getKey().toString();
                    descriptor.subCommands[i] = CommandDescriptorFactory
                            .fromCommand(entries[i].getValue(), false);
                }
            }

            descriptor.parameters.put("subCommandValues", subCommandValues);
        }
    };

    public static final CommandDescriber<SequentialCommandGroup> sequentialCommandGroupDescriber = (descriptor,
            command, isRunning) -> {
        int currentCommandIndex = getPrivateField(SequentialCommandGroup.class, "m_currentCommandIndex")
                .getInt(command);

        List<Command> subCommands = ((List<Command>) getPrivateField(SequentialCommandGroup.class, "m_commands")
                .get(command));

        descriptor.subCommands = new CommandDescriptor[subCommands.size()];
        for (int i = 0; i < subCommands.size(); i++) {
            descriptor.subCommands[i] = CommandDescriptorFactory.fromCommand(subCommands.get(i),
                    isRunning && i == currentCommandIndex);
        }
    };

    public static final CommandDescriber<WaitCommand> waitCommandDescriber = (descriptor, command, isRunning) -> {
        descriptor.parameters.put("duration",
                getPrivateField(WaitCommand.class, "m_duration").getDouble(command));
        descriptor.parameters.put("timeElapsed",
                ((Timer) getPrivateField(WaitCommand.class, "m_timer").get(command)).get());
    };

    public static final CommandDescriber<WaitUntilCommand> waitUntilCommandDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.parameters.put("condition",
                ((BooleanSupplier) getPrivateField(WaitUntilCommand.class, "m_condition")
                        .get(command)).getAsBoolean());
    };

    public static final CommandDescriber<WrapperCommand> wrapperCommandDescriber = (descriptor, command,
            isRunning) -> {
        descriptor.subCommands = new CommandDescriptor[] {
                CommandDescriptorFactory.fromCommand(
                        (Command) getPrivateField(WrapperCommand.class, "m_command")
                                .get(command),
                        isRunning) };
    };

    public static void registerAll() {
        CommandDescriptorFactory.registerDescriber(ConditionalCommand.class, conditionalCommandDescriber);
        CommandDescriptorFactory.registerDescriber(ParallelCommandGroup.class, parallelCommandGroupDescriber);
        CommandDescriptorFactory.registerDescriber(ParallelDeadlineGroup.class, parallelDeadlineGroupDescriber);
        CommandDescriptorFactory.registerDescriber(ParallelRaceGroup.class, parallelRaceGroupDescriber);
        CommandDescriptorFactory.registerDescriber(RepeatCommand.class, repeatCommandDescriber);
        CommandDescriptorFactory.registerDescriber(ScheduleCommand.class, scheduleCommandDescriber);
        CommandDescriptorFactory.registerDescriber(SelectCommand.class, selectCommandDescriber);
        CommandDescriptorFactory.registerDescriber(SequentialCommandGroup.class, sequentialCommandGroupDescriber);
        CommandDescriptorFactory.registerDescriber(WaitCommand.class, waitCommandDescriber);
        CommandDescriptorFactory.registerDescriber(WaitUntilCommand.class, waitUntilCommandDescriber);
        CommandDescriptorFactory.registerDescriber(WrapperCommand.class, wrapperCommandDescriber);
    }

    private static Field getPrivateField(Class<?> clazz, String name) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
