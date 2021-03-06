package de.dytanic.cloudnet.command;

import java.util.Collection;

/**
 * Represents a map from that all commands will manage.
 *
 * @see Command
 * @see CommandInfo
 * @see ICommandExecutor
 */
public interface ICommandMap {

    /**
     * Register a new command instance with all names that are is configured
     *
     * @param command the command that should registered
     */
    void registerCommand(Command command);

    /**
     * Unregister all commands from the map, that has the following string as name
     *
     * @param command the command name that should remove from the map
     */
    void unregisterCommand(String command);

    /**
     * Unregister all commands from the map, that command instance class is like the argument
     *
     * @param command the class reference that should remove from the map
     */
    void unregisterCommand(Class<? extends Command> command);

    /**
     * Unregister all commands from the classLoader instance.
     *
     * @param classLoader the classLoader from that all commands, that are contain will remove
     */
    void unregisterCommands(ClassLoader classLoader);

    /**
     * Remove all commands from the command map
     */
    void unregisterCommands();

    /**
     * Transform all commands instances that are contain in the map into a CommandInfo object
     */
    Collection<CommandInfo> getCommandInfos();

    /**
     * Returns all command names that are contain in the map
     */
    Collection<String> getCommandNames();

    /**
     * Returns the command in the map with a specific name. If the name is more than one exist. The first command instance that could be found
     * will return
     *
     * @param name the name, from that the command should resolve
     * @return first command instance that could be found
     */
    Command getCommand(String name);

    /**
     * Returns the command object from the custom command line
     */
    Command getCommandFromLine(String commandLine);

    /**
     * Invokes a command execute() method when there is contain in the map and parsed from the command line the additional properties.
     *
     * @param commandSender the command sender of the commandLine that should use
     * @param commandLine   the following commandline that should dispatch
     * @return true if the command will successful executed or false when the command cannot be found in the map or an exception was thrown
     */
    boolean dispatchCommand(ICommandSender commandSender, String commandLine);

    /*= ----------------------------------------- =*/

    /**
     * Register new command instances with all names that are is configured
     *
     * @param commands the commands that should registered
     */
    default void registerCommand(Command... commands)
    {
        if (commands != null)
            for (Command command : commands)
                if (command != null)
                    registerCommand(command);
    }

    /**
     * Unregister all commands from the map, that has the following string as name
     *
     * @param commands the command names that should remove from the map
     */
    default void unregisterCommand(String... commands)
    {
        if (commands != null)
            for (String command : commands)
                if (command != null)
                    unregisterCommand(command);
    }

    /**
     * Unregister all commands from the map, that command instance classes is like the argument
     *
     * @param commands the class references that should remove from the map
     */
    default void unregisterCommand(Class<? extends Command>... commands)
    {
        if (commands != null)
            for (Class<? extends Command> c : commands)
                if (c != null)
                    unregisterCommand(c);
    }
}