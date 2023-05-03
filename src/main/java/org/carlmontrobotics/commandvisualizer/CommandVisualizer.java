package org.carlmontrobotics.commandvisualizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Consumer;

import org.carlmontrobotics.lib199.Lib199Subsystem;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

public class CommandVisualizer {

    private static HashSet<Consumer<String>> loggers = new HashSet<>();
    private static HashSet<Command> runningCommands = new HashSet<>();
    private static boolean disabled = false;

    static {
        Lib199Subsystem.registerPeriodic(CommandVisualizer::logCommands);
        CommandScheduler.getInstance().onCommandInitialize(runningCommands::add);
        CommandScheduler.getInstance().onCommandFinish(runningCommands::remove);
        CommandScheduler.getInstance().onCommandInterrupt(runningCommands::remove);
    }

    public static void registerLogger(Consumer<String> logger) {
        loggers.add(logger);
    }

    public static void logCommands() {
        if (disabled)
            return;
        for (Consumer<String> logger : loggers) {
            logger.accept(CommandVisualizer.formatRunningCommands());
        }
    }

    public static void disable() {
        disabled = true;
    }

    public static void enable() {
        disabled = false;
    }

    public static ArrayList<Command> getRunningCommands() {
        return new ArrayList<>(runningCommands);
    }

    public static CommandDescriptor[] getProcessedRunningCommands() {
        return runningCommands.stream().map(command -> {
            try {
                return CommandDescriptor.fromCommandWrappingExceptions(command, true);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        })
                .filter(Objects::nonNull)
                .toArray(CommandDescriptor[]::new);
    }

    public static String formatRunningCommands() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        {
            sb.append("\"commands\": [");
            boolean valuesWritten = false;
            {
                for (CommandDescriptor command : getProcessedRunningCommands()) {
                    try {
                        sb.append(command.toJson());
                        sb.append(",");
                        valuesWritten = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (valuesWritten)
                sb.delete(sb.length() - 1, sb.length());
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

}
