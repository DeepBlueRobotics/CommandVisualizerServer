package org.carlmontrobotics.commandvisualizer;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.wpi.first.wpilibj2.command.Command.InterruptionBehavior;

public class CommandDescriptor {

    public String name, clazz, describer;
    public boolean isRunning, runsWhenDisabled, isComposed;
    public Map<String, Object> parameters = new HashMap<>();
    public InterruptionBehavior interruptionBehavior = InterruptionBehavior.kCancelSelf;
    public CommandDescriptor[] subCommands = new CommandDescriptor[0];
    public String[] requirements = new String[0];

    public String toJson() throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(this);
    }

}
