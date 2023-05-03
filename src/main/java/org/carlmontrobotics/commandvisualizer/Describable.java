package org.carlmontrobotics.commandvisualizer;

public interface Describable {

    public void describe(CommandDescriptor descriptor, boolean isRunning) throws Exception;

}
