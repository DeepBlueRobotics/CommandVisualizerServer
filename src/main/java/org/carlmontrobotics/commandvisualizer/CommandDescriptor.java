package org.carlmontrobotics.commandvisualizer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Command.InterruptionBehavior;
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

public class CommandDescriptor {

    public String name, clazz;
    public boolean isRunning, runsWhenDisabled;
    public Map<String, Object> parameters = new HashMap<>();
    public InterruptionBehavior interruptionBehavior = InterruptionBehavior.kCancelSelf;
    public CommandDescriptor[] subCommands = new CommandDescriptor[0];
    public String[] requirements = new String[0];

    public static CommandDescriptor fromCommandWrappingExceptions(Command command, boolean isRunning)
            throws WrapperException {
        try {
            return fromCommand(command, isRunning);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new WrapperException(e);
        }
    }

    @SuppressWarnings("unchecked")
    // Can't use Command.isScheduled() because nested commands are not scheduled
    public static CommandDescriptor fromCommand(Command command, boolean isRunning)
            throws IllegalAccessException, NoSuchFieldException {
        CommandDescriptor descriptor = new CommandDescriptor();

        descriptor.name = command.getName();
        descriptor.clazz = command.getClass().getName();
        descriptor.isRunning = isRunning;
        descriptor.runsWhenDisabled = command.runsWhenDisabled();
        descriptor.interruptionBehavior = command.getInterruptionBehavior();
        descriptor.subCommands = new CommandDescriptor[0];
        descriptor.requirements = command.getRequirements().stream().map(Object::getClass)
                .map(clazz -> clazz.getName()).toArray(String[]::new);

        if (command instanceof ConditionalCommand) {
            boolean condition = ((BooleanSupplier) getPrivateField(ConditionalCommand.class, "m_condition")
                    .get(command)).getAsBoolean();
            descriptor.parameters.put("condition", condition);
            descriptor.subCommands = new CommandDescriptor[2];
            descriptor.subCommands[0] = fromCommand(
                    (Command) getPrivateField(ConditionalCommand.class, "m_onTrue").get(command),
                    isRunning && condition);
            descriptor.subCommands[1] = fromCommand(
                    (Command) getPrivateField(ConditionalCommand.class, "m_onFalse").get(command),
                    isRunning && !condition);

        } else if (command instanceof NotifierCommand) {

            descriptor.parameters.put("period", getPrivateField(NotifierCommand.class, "m_period").getDouble(command));

        } else if (command instanceof ParallelCommandGroup) {

            descriptor.subCommands = ((Map<Command, Boolean>) getPrivateField(ParallelCommandGroup.class, "m_commands")
                    .get(command))
                    .entrySet().stream()
                    .map(entry -> CommandDescriptor.fromCommandWrappingExceptions(entry.getKey(), entry.getValue()))
                    .toArray(CommandDescriptor[]::new);

        } else if (command instanceof ParallelDeadlineGroup) {

            Set<Command> subCommands = ((Map<Command, Boolean>) getPrivateField(ParallelDeadlineGroup.class,
                    "m_commands").get(command)).keySet();

            descriptor.subCommands = new CommandDescriptor[subCommands.size()];
            // Ensure deadline is first
            descriptor.subCommands[0] = fromCommand(
                    (Command) getPrivateField(ParallelDeadlineGroup.class, "m_deadline").get(command), isRunning);
            CommandDescriptor[] additionalSubCommands = ((Map<Command, Boolean>) getPrivateField(
                    ParallelDeadlineGroup.class, "m_commands").get(command))
                    .entrySet().stream()
                    .map(entry -> CommandDescriptor.fromCommandWrappingExceptions(entry.getKey(), entry.getValue()))
                    .filter(Predicate.not(Predicate.isEqual(descriptor.subCommands[0])))
                    .toArray(CommandDescriptor[]::new);
            System.arraycopy(additionalSubCommands, 0, descriptor.subCommands, 1, additionalSubCommands.length);

        } else if (command instanceof ParallelRaceGroup) {

            descriptor.subCommands = ((Set<Command>) getPrivateField(ParallelRaceGroup.class, "m_commands")
                    .get(command))
                    .stream().map(subCommand -> CommandDescriptor.fromCommandWrappingExceptions(command, isRunning))
                    .toArray(CommandDescriptor[]::new);

        } else if (command instanceof ProxyCommand) {

            if (isRunning)
                descriptor.subCommands = new CommandDescriptor[] {
                        fromCommand((Command) getPrivateField(ProxyCommand.class, "m_command").get(command),
                                isRunning) };

        } else if (command instanceof ProxyScheduleCommand) {

            descriptor.subCommands = ((Set<Command>) getPrivateField(ParallelRaceGroup.class, "m_toSchedule")
                    .get(command))
                    .stream()
                    .map(subCommand -> CommandDescriptor.fromCommandWrappingExceptions(command,
                            CommandVisualizer.getRunningCommands().contains(subCommand)))
                    .toArray(CommandDescriptor[]::new);

        } else if (command instanceof RepeatCommand) {

            descriptor.subCommands = new CommandDescriptor[] {
                    fromCommand((Command) getPrivateField(RepeatCommand.class, "m_command").get(command), isRunning) };

        } else if (command instanceof ScheduleCommand) {

            descriptor.subCommands = ((Set<Command>) getPrivateField(ParallelRaceGroup.class, "m_toSchedule")
                    .get(command))
                    .stream()
                    .map(subCommand -> CommandDescriptor.fromCommandWrappingExceptions(command,
                            CommandVisualizer.getRunningCommands().contains(subCommand)))
                    .toArray(CommandDescriptor[]::new);

        } else if (command instanceof SelectCommand) {

            Object currentSelectorValue = ((Supplier<Object>) getPrivateField(SelectCommand.class, "m_selector")
                    .get(command)).get();
            descriptor.parameters.put("currentSelectorValue",
                    currentSelectorValue == null ? "<null>" : currentSelectorValue.toString());

            Map<Object, Command> subCommands = (Map<Object, Command>) getPrivateField(SelectCommand.class, "m_commands")
                    .get(command);

            if (subCommands == null) {
                descriptor.parameters.put("hasSupplier", true);
                descriptor.parameters.put("subCommandValues", new String[0]);
                if (isRunning)
                    descriptor.subCommands = new CommandDescriptor[] { fromCommand(
                            (Command) getPrivateField(SelectCommand.class, "m_selectedCommand").get(command), true) };
            } else {
                descriptor.parameters.put("hasSupplier", false);
                String[] subCommandValues = new String[subCommands.size()];
                descriptor.subCommands = new CommandDescriptor[subCommands.size()];

                Map.Entry<Object, Command>[] entries = subCommands.entrySet().toArray(Map.Entry[]::new);

                if (isRunning) {
                    descriptor.subCommands[0] = fromCommand(
                            (Command) getPrivateField(SelectCommand.class, "m_selectedCommand").get(command), true);
                    int arrIdx = 1;
                    for (int i = 0; i < entries.length; i++) {
                        if (entries[i].getKey() == currentSelectorValue) {
                            subCommandValues[0] = entries[i].getKey().toString();
                            continue;
                        }

                        subCommandValues[arrIdx] = entries[i].getKey().toString();
                        descriptor.subCommands[arrIdx] = fromCommand(entries[i].getValue(), false);
                        arrIdx++;
                    }
                } else {
                    for (int i = 0; i < entries.length; i++) {
                        subCommandValues[i] = entries[i].getKey().toString();
                        descriptor.subCommands[i] = fromCommand(entries[i].getValue(), false);
                    }
                }

                descriptor.parameters.put("subCommandValues", subCommandValues);
            }

        } else if (command instanceof SequentialCommandGroup) {

            int currentCommandIndex = getPrivateField(SequentialCommandGroup.class, "m_currentCommandIndex")
                    .getInt(command);

            List<Command> subCommands = ((List<Command>) getPrivateField(SequentialCommandGroup.class, "m_commands")
                    .get(command));

            descriptor.subCommands = new CommandDescriptor[subCommands.size()];
            for (int i = 0; i < subCommands.size(); i++) {
                descriptor.subCommands[i] = fromCommand(subCommands.get(i), isRunning && i == currentCommandIndex);
            }

        } else if (command instanceof WaitCommand) {

            descriptor.parameters.put("duration", getPrivateField(WaitCommand.class, "m_duration").getDouble(command));
            descriptor.parameters.put("timeElapsed",
                    ((Timer) getPrivateField(WaitCommand.class, "m_timer").get(command)).get());

        } else if (command instanceof WaitUntilCommand) {

            descriptor.parameters.put("condition",
                    ((BooleanSupplier) getPrivateField(WaitUntilCommand.class, "m_condition")
                            .get(command)).getAsBoolean());

        } else if (command instanceof WrapperCommand) {

            descriptor.subCommands = new CommandDescriptor[] {
                    fromCommand((Command) getPrivateField(WrapperCommand.class, "m_command").get(command), isRunning) };

        }

        return descriptor;
    }

    private static Field getPrivateField(Class<?> clazz, String name) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public String toJson() throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(this);
    }

}
