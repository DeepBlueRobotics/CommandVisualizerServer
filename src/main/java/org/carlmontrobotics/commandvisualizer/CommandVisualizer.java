package org.carlmontrobotics.commandvisualizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.carlmontrobotics.lib199.Lib199Subsystem;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

public class CommandVisualizer {

    public static final String NT_KEY = "CommandDescriptors";

    private static Set<Consumer<String>> loggers = new HashSet<>();
    private static Set<Command> runningCommands = new HashSet<>();
    private static Set<Command> allCommands = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean disabled = false;

    static {
        Lib199Subsystem.registerPeriodic(CommandVisualizer::logCommands);
        CommandScheduler.getInstance().onCommandInitialize(allCommands::add);
        CommandScheduler.getInstance().onCommandInitialize(runningCommands::add);
        CommandScheduler.getInstance().onCommandFinish(runningCommands::remove);
        CommandScheduler.getInstance().onCommandInterrupt(runningCommands::remove);
    }

    public static void registerLogger(Consumer<String> logger) {
        loggers.add(logger);
    }

    public static void registerDefaultNTLogger() {
        NetworkTableEntry entry = NetworkTableInstance.getDefault().getEntry(NT_KEY);
        registerLogger(entry::setString);
    }

    public static void logCommands() {
        if (disabled)
            return;
        String descriptorJson;
        try {
            descriptorJson = new ObjectMapper().writeValueAsString(getProcessedCommands());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        for (Consumer<String> logger : loggers) {
            logger.accept(descriptorJson);
        }
    }

    public static void disable() {
        disabled = true;
    }

    public static void enable() {
        disabled = false;
    }

    public static ArrayList<Command> getAllCommands() {
        return new ArrayList<>(allCommands);
    }

    public static ArrayList<Command> getRunningCommands() {
        return new ArrayList<>(runningCommands);
    }

    public static CommandDescriptor[] getProcessedCommands() {
        return allCommands.stream().map(command -> {
            try {
                return CommandDescriptorFactory.fromCommand(command, runningCommands.contains(command));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        })
                .filter(Objects::nonNull)
                .toArray(CommandDescriptor[]::new);
    }

}
