package org.carlmontrobotics.commandvisualizer;

import edu.wpi.first.wpilibj2.command.Command;

public interface CommandDescriber<T extends Command> {

    public void describe(CommandDescriptor descriptor, T command, boolean isRunning) throws Exception;

}
