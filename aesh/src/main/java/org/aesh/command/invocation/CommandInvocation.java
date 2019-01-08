/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.aesh.command.invocation;

import java.io.IOException;
import org.aesh.command.Executor;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.AeshContext;
import org.aesh.command.shell.Shell;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.readline.Prompt;
import org.aesh.readline.action.KeyAction;

/**
 * A CommandInvocation is the value object passed to a Command when it is executed.
 * It contain references to the current ControlOperator, registry, shell, ++
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface CommandInvocation {

    /**
     * @return the shell
     */
    Shell getShell();

    /**
     * Specify the prompt
     */
    void setPrompt(Prompt prompt);

    /**
     * @return Get the current Prompt
     */
    Prompt getPrompt();

    /**
     * @return a formatted usage/help info from the specified command
     */
    String getHelpInfo(String commandName);

    /**
     * @return the command single-line description
     */
    String getCommandDescription(String commandName);

    /**
     * Stop the console and end the session
     */
    void stop();

    /**
     * Get AeshContext
     */
    @Deprecated
    AeshContext getAeshContext();

    /**
     * Get the configuration.
     *
     * @return The configuration.
     */
    CommandInvocationConfiguration getConfiguration();
    /**
     * A blocking call that will return user input from the terminal
     *
     * @return user input
     * @throws InterruptedException
     */
    KeyAction input() throws InterruptedException;

    /**
     * A blocking call that will return user input from the terminal
     * after the user has pressed enter.
     *
     * @return user input line
     * @throws InterruptedException
     */
    String inputLine() throws InterruptedException;

    /**
     * A blocking call that will return user input from the terminal
     * after the user has pressed enter.
     *
     * @return user input line
     * @throws InterruptedException
     */
    String inputLine(Prompt prompt) throws InterruptedException;

    /**
     * This will push the input to the input stream where aesh will
     * parse it and execute it as a normal "user input".
     * The input will not be visible for the user.
     * Note that if this command still has the foreground this input
     * will just be sitting on the queue.
     *
     * @param input command input
     */
    void executeCommand(String input) throws CommandNotFoundException,
            CommandLineParserException,
            OptionValidatorException,
            CommandValidatorException,
            CommandException,
            InterruptedException,
            IOException;

    Executor<? extends CommandInvocation> buildExecutor(String line) throws CommandNotFoundException,
            CommandLineParserException,
            OptionValidatorException,
            CommandValidatorException,
            IOException;

   /**
    * Print a message on console
    * @param msg
    */
    default void print(String msg) {
        print(msg, false);
    }

    /**
     * Print a new line with a message on console;
     * @param msg
     */
    default void println(String msg) {
        println(msg, false);
    }

    /**
    * Print a message on console
    * @param msg
     * @param paging true to pause output for long content
    */
    void print(String msg, boolean paging);

    /**
     * Print a new line with a message on console;
     * @param msg
     * @param paging true to pause output for long content
     */
    void println(String msg, boolean paging);

}
