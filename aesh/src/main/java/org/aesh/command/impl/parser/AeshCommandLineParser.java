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
package org.aesh.command.impl.parser;

import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.invocation.InvocationProviders;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.parser.OptionParserException;
import org.aesh.command.parser.RequiredOptionException;
import org.aesh.command.populator.CommandPopulator;
import org.aesh.command.Command;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.parser.ParserStatus;
import org.aesh.readline.AeshContext;
import org.aesh.parser.LineParser;
import org.aesh.parser.ParsedLine;
import org.aesh.parser.ParsedLineIterator;
import org.aesh.parser.ParsedWord;
import org.aesh.utils.Config;

import java.util.ArrayList;
import java.util.List;

import org.aesh.command.map.MapCommand;
import org.aesh.command.map.MapCommandPopulator;
import org.aesh.command.map.MapProcessedCommandBuilder.MapProcessedCommand;

/**
 * A simple command line parser.
 * It parses a given string based on the Command given and
 *
 * It can also print a formatted usage/help information.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AeshCommandLineParser<C extends Command> implements CommandLineParser<C> {

    private final ProcessedCommand<C> processedCommand;
    private List<CommandLineParser<C>> childParsers;
    private boolean isChild = false;
    private ProcessedOption lastParsedOption;
    private boolean parsedCommand = false;
    private final LineParser lineParser;
    private CompleteStatus completeStatus;

    public AeshCommandLineParser(ProcessedCommand<C> processedCommand) {
        this.processedCommand = processedCommand;
        lineParser = new LineParser();
    }

    @Override
    public void addChildParser(CommandLineParser<C> commandLineParser) throws CommandLineParserException {
        if(processedCommand.hasArgument() || processedCommand.hasArguments())
            throw new CommandLineParserException("Group commands can not have arguments defined");
        if(childParsers == null)
            childParsers = new ArrayList<>();
        commandLineParser.setChild(true);
        childParsers.add(commandLineParser);
    }

    public List<CommandLineParser<C>> getChildParsers() {
        return childParsers;
    }

    @Override
    public void setChild(boolean child) {
        isChild = child;
    }

    @Override
    public CommandLineParser<C> parsedCommand() {
        if(parsedCommand)
            return this;
        else if(isGroupCommand()) {
            CommandLineParser<C> correct;
            for(CommandLineParser<C> child : childParsers) {
                correct = child.parsedCommand();
                if(correct != null)
                    return correct;
            }
        }
        return null;
    }

    @Override
    public void complete(AeshCompleteOperation completeOperation, InvocationProviders invocationProviders) {

        complete(completeOperation,
                new LineParser().parseLine(completeOperation.getBuffer(), completeOperation.getCursor(), true),
                invocationProviders);
    }

    @Override
    public void complete(AeshCompleteOperation completeOperation, ParsedLine line, InvocationProviders invocationProviders) {
        //first parse
        parse(line.iterator(), Mode.COMPLETION);
        //then use completion parser to populate completeOperation
        parsedCommand().getCompletionParser().injectValuesAndComplete(completeOperation, invocationProviders, line);
    }

    @Override
    public List<String> getAllNames() {
        if (isGroupCommand()) {
            List<CommandLineParser<C>> parsers = getChildParsers();
            List<String> names = new ArrayList<>(parsers.size());
            for (CommandLineParser child : parsers) {
                names.add(processedCommand.name()+" "+child.getProcessedCommand().name());
            }
            return names;
        }
        else {
            List<String> names = new ArrayList<>(1);
            names.add(processedCommand.name());
            return names;
        }
    }

    public boolean isChild() {
        return isChild;
    }

    @Override
    public CommandLineParser<C> getChildParser(String name) {
        if(!isGroupCommand())
            return null;
        for (CommandLineParser clp : getChildParsers()) {
            if(clp.getProcessedCommand().name().equals(name))
                return clp;
        }
        return null;
    }

    @Override
    public List<CommandLineParser<C>> getAllChildParsers() {
        if(isGroupCommand())
            return getChildParsers();
        else
           return new ArrayList<>();
    }

    @Override
    public ProcessedCommand<C> getProcessedCommand() {
        return processedCommand;
    }

    @Override
    public C getCommand() {
        return processedCommand.getCommand();
    }

    @Override
    public CommandLineCompletionParser getCompletionParser() {
        return new AeshCommandLineCompletionParser(this);
    }

    @Override
    public CommandPopulator<Object, C> getCommandPopulator() {
        return processedCommand.getCommandPopulator();
    }

    @Override
    public void populateObject(String line, InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
        //first parse, then populate
        parse(line, mode);
        if(mode == Mode.VALIDATE && getProcessedCommand().parserExceptions().size() > 0) {
            throw getProcessedCommand().parserExceptions().get(0);
        }
        else {
            doPopulate(processedCommand, invocationProviders, aeshContext, mode);
       }
    }

    @Override
    public void doPopulate(ProcessedCommand processedCommand, InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
        getCommandPopulator().populateObject(processedCommand, invocationProviders, aeshContext, mode);
        if(isGroupCommand()) {
            for(CommandLineParser parser : getChildParsers()) {
                parser.doPopulate(parser.getProcessedCommand(), invocationProviders, aeshContext, mode);
            }
        }
    }

    /**
     * Returns a usage String based on the defined command and options.
     * Useful when printing "help" info etc.
     */
    @Override
    public String printHelp() {
        List<CommandLineParser<C>> parsers = getChildParsers();
        if (parsers != null && parsers.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(processedCommand.printHelp())
                    .append(Config.getLineSeparator())
                    .append(processedCommand.name())
                    .append(" commands:")
                    .append(Config.getLineSeparator());
            for (CommandLineParser child : parsers)
                sb.append("    ")
                  .append(child.getProcessedCommand().name())
                  .append("  ")
                  .append(child.getProcessedCommand().description())
                  .append(Config.getLineSeparator());

            return sb.toString();
        }
        else
            return processedCommand.printHelp();
    }

    @Override
    public String printDescription() {
        List<CommandLineParser<C>> parsers = getChildParsers();
        if (parsers != null && parsers.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(processedCommand.name())
                    .append(" : ");
            sb.append(processedCommand.printDescription())
                    .append(Config.getLineSeparator());
            for (int i = 0; i < parsers.size(); i++) {
                CommandLineParser<C> parser = parsers.get(i);
                sb.append("    ")
                        .append(parser.getProcessedCommand().name())
                        .append(" : ");
                if (!parser.getProcessedCommand().getOptions().isEmpty()) {
                    sb.append("    ");
                    sb.append(parser.getProcessedCommand().printDescription(8));
                } else {
                    sb.append(parser.getProcessedCommand().description());
                }
                if (parsers.size() > i + 1) {
                    sb.append(Config.getLineSeparator());
                }

            }

            return sb.toString();
        }
        return processedCommand.name() + " : " + processedCommand.printDescription();
    }

    /**
     * Parse a command line with the defined command as base of the rules.
     * If any options are found, but not defined in the command object an
     * CommandLineParserException will be thrown.
     * Also, if a required option is not found or options specified with value,
     * but is not given any value an OptionParserException will be thrown.
     *
     * @param line input
     */
    @Override
    public void parse(String line) {
        parse(line, Mode.STRICT);
    }

    @Override
    public void parse(ParsedLineIterator iterator, Mode mode) {
        clear();
        if(iterator.hasNextWord()) {
            String command = iterator.pollWord();
            if (processedCommand.name().equals(command)
                    || processedCommand.getAliases().contains(command)) {
                if(isGroupCommand() && iterator.hasNextWord()) {
                   CommandLineParser<C> clp = getChildParser(iterator.peekWord());
                    if(clp == null) {
                        //if the user have written garbage in the next word, we need to check
                        // eg: group GARBAGE <tab>
                        if(iterator.isNextWordCursorWord() ||
                                iterator.peekWord().startsWith("--") || iterator.peekWord().startsWith("-"))
                            doParse(iterator, mode);
                        else {
                            processedCommand.addParserException(new CommandLineParserException("Wrong input for group command."));
                            if(mode == Mode.COMPLETION) {
                                parsedCommand = true;
                                processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.INVALID_INPUT, ""));
                            }
                        }
                    }
                    //we have a group command
                    else {
                        //remove the child name
                        clp.parse(iterator, mode);
                    }
                }
                else
                    doParse(iterator, mode);
            }
        }
        else if(iterator.parserError() != null)
            processedCommand.addParserException(new CommandLineParserException(iterator.parserError()));
    }


    private void doParse(ParsedLineIterator iter, Mode mode) {
        parsedCommand = true;
        if(mode == Mode.COMPLETION)
            doParseCompletion(iter);
        else {
            try {
                boolean argumentMarker = false;
                while (iter.hasNextWord()) {
                    ParsedWord word = iter.peekParsedWord();
                    if(argumentMarker) {
                        setArgStatus(word.word());
                        iter.pollParsedWord();
                    }
                    else {
                        lastParsedOption = processedCommand.searchAllOptions(word.word());
                        if(lastParsedOption != null) {
                            lastParsedOption.parser().parse(iter, lastParsedOption);
                        }
                        else {
                            //if we have a -- and its not at the end of the line it is used as a
                            //marker to signal that all the values after it are arguments, so we will ignore this
                            if(word.word().equals("--") && !iter.isNextWordCursorWord()) {
                                argumentMarker = true;
                            }
                            else {
                                // Unknown commands are possible with a dynamic command (MapCommand)
                                // In this case we shouldn't validate the option and pass it down to
                                // the populator for Map injection.
                                boolean unknown = false;
                                if(word.word().startsWith("-")) {
                                    if(word.word().startsWith("--") || word.word().length() == 2) {
                                        // invalid short names and long names should be rejected.
                                        if(!(processedCommand.getCommand() instanceof MapCommand)) {
                                            processedCommand.addParserException(
                                                    new OptionParserException("The option " + word.word()
                                                                                      + " is unknown."));
                                        } else {
                                            unknown = true;
                                        }
                                    }
                                }
                                if(unknown) {
                                    // Pass down the option directly to the populator.
                                    MapCommandPopulator pop = (MapCommandPopulator) processedCommand.getCommandPopulator();
                                    pop.addUnknownOption(word.word());
                                }
                                else {
                                    setArgStatus(word.word());
                                }
                            }
                            iter.pollParsedWord();
                        }
                    }
                }
            }
            catch (OptionParserException ope) {
                processedCommand.addParserException(ope);
            }
            if (mode == Mode.STRICT) {
                if (processedCommand instanceof MapProcessedCommand) {
                    MapCommand mc = (MapCommand) processedCommand.getCommand();
                    if (!mc.checkForRequiredOptions(iter.baseLine())) {
                        return;
                    }
                }
                RequiredOptionException re = checkForMissingRequiredOptions(processedCommand);
                if (re != null)
                    processedCommand.addParserException(re);
            }
        }
    }

    private void setArgStatus(String word) {
        if (processedCommand.hasArguments()) {
            processedCommand.getArguments().addValue(word);
        }
        else if (processedCommand.hasArgumentWithNoValue()) {
            processedCommand.getArgument().addValue(word);
        }
        else {
            processedCommand.addParserException(
                    new OptionParserException("A value " + word + " was given as an argument, but the command do not support it."));
        }
    }

    private void doParseCompletion(ParsedLineIterator iter) {
        if (processedCommand instanceof MapProcessedCommand) {
            ((MapProcessedCommand) processedCommand).setMode(Mode.COMPLETION);
        }
        if(!iter.hasNextWord()) {
            if(isGroupCommand())
                processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.GROUP_COMMAND, ""));
            else {
                //child commands that ends after its name, must be able to append space
                if(iter.baseLine().size() == (iter.baseLine().selectedIndex()+1) &&
                        lastParsedOption == null) {
                    //append space
                    if(iter.baseLine().status() == ParserStatus.OK)
                        processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.APPEND_SPACE, ""));
                    //we have unclosed quote, lets parse it as an argument
                    else
                        processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.ARGUMENT, ""));
                }
                //we list all the options
                else
                    processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.COMPLETE_OPTION, ""));
            }
        }
        else {
            try {
                boolean argumentMarker = false; //argumentMarker is set to true if we have found "--" inside the line
                while(iter.hasNextWord()) {
                    //first check if we have passed the selected word, if so lets stop
                    if(iter.baseLine().selectedIndex() > -1 &&
                               iter.pastCursorWord() &&
                               processedCommand.completeStatus() != null)
                        return;
                    ParsedWord word = iter.peekParsedWord();
                    //first check if argumentMarker has been set
                    if(argumentMarker) {
                        setCompletionArgStatus(word.word());
                        iter.pollParsedWord();
                    }
                    else {
                        lastParsedOption = processedCommand.searchAllOptions(word.word());
                        if(lastParsedOption != null) {
                            //if current word is cursor word, we need to check if the current option name
                            //might be part of another option name: eg: list and listFolders
                            if(iter.isNextWordCursorWord() && !word.word().contains("=")
                                       && processedCommand.findPossibleLongNames(word.word()).size() > 1) {
                                processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.LONG_OPTION, word.word().substring(2)));
                                iter.pollParsedWord();
                            } else {
                                lastParsedOption.parser().parse(iter, lastParsedOption);
                                if(!iter.hasNextWord()) {
                                    if(lastParsedOption.hasValue() || iter.baseLine().spaceAtEnd())
                                        processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.COMPLETE_OPTION, ""));
                                        //if the option do not have any value, set missing value status for easier processing
                                    else
                                        processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.OPTION_MISSING_VALUE, ""));
                                }
                            }
                        }
                        //if we have -- that stands alone it's a marker for separation of options and arguments
                        else if(word.word().equals("--") && !iter.isNextWordCursorWord()) {
                            argumentMarker = true;
                            iter.pollParsedWord();
                        }
                        //got a partial option
                        else if(word.word().startsWith("--")) {
                            processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.LONG_OPTION, word.word().substring(2)));
                            iter.pollParsedWord();
                        } else if(word.word().startsWith("-")) {
                            processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.SHORT_OPTION, word.word().substring(1)));
                            iter.pollParsedWord();
                        }
                        //we're completing arguments or group command names
                        else {
                            //only set group command if nothing else is set
                            if(lastParsedOption == null && isGroupCommand()) {
                                if(iter.isNextWordCursorWord())
                                    processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.GROUP_COMMAND, word.word()));
                                else if(iter.baseLine().cursorAtEnd() && iter.baseLine().spaceAtEnd())
                                    processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.GROUP_COMMAND, ""));
                            } else if(iter.isNextWordCursorWord()) {
                                if(processedCommand.getArguments() != null ||
                                        (processedCommand.getArgument() != null && processedCommand.getArgument().getValue() == null)) {
                                    processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.ARGUMENT, word.word()));
                                } else {
                                    processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.ARGUMENT_ERROR, null));
                                }
                            } else {
                                setCompletionArgStatus(word.word());
                            }
                            iter.pollParsedWord();
                        }
                    }
                }
                if(argumentMarker && processedCommand.completeStatus() == null)
                    setCompletionArgStatus(null);
            }
            catch (OptionParserException e) {
                //TODO: needs to be improved
                //ignored for now
                processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.OPTION_MISSING_VALUE, ""));
            }
        }
    }

    private void setCompletionArgStatus(String word) {
        //add the value to argument/arguments
        if(processedCommand.hasArguments()) {
            processedCommand.getArguments().addValue(word);
            processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.ARGUMENT, null));
        }
        else if(processedCommand.hasArgument()) {
            if(processedCommand.getArgument().getValue() == null) {
                processedCommand.getArgument().addValue(word);
                processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.ARGUMENT, null));
            }
            //if we add more than one value to argument we set error status
            else
                processedCommand.setCompleteStatus(new CompleteStatus(CompleteStatus.Status.ARGUMENT_ERROR, null));
        }
    }

    private RequiredOptionException checkForMissingRequiredOptions(ProcessedCommand<C> command) {
        for(ProcessedOption o : command.getOptions()) {
            if(doCheckForMissingRequiredOption(o))
                return new RequiredOptionException("Option: "+o.getDisplayName()+" is required for this command.");
        }
        if(command.getArgument() != null) {
            if (doCheckForMissingRequiredOption(command.getArgument()))
                return new RequiredOptionException("Argument is required for this command.");
        }
        else if(command.getArguments() != null)
            if(doCheckForMissingRequiredOption(command.getArguments()))
                return new RequiredOptionException("Arguments is required for this command.");

        return null;
    }

    private boolean doCheckForMissingRequiredOption(ProcessedOption o) {
        if (o.isRequired() && o.getValue() == null) {
            boolean found = false;
            for (ProcessedOption po : processedCommand.getOptions()) {
                if (po.getValue() != null && po.doOverrideRequired()) {
                    found = true;
                    break;
                }
            }
            if (!found)
                return true;
        }
        return false;
    }

    @Override
    public ProcessedOption lastParsedOption() {
        return lastParsedOption;
    }

    /**
     * Parse a command line with the defined command as base of the rules.
     * If any options are found, but not defined in the command object an
     * CommandLineParserException will be thrown.
     * Also, if a required option is not found or options specified with value,
     * but is not given any value an CommandLineParserException will be thrown.
     *
     * @param line input
     * @param mode parser mode
     */
    @Override
    public void parse(String line, Mode mode) {
        parse(lineParser.parseLine(line, line.length()).iterator(), mode);
    }

    @Override
    public void clear() {
        //if this is the parsed command, clear it
        if(parsedCommand) {
            processedCommand.clear();
            lastParsedOption = null;
            parsedCommand = false;
            completeStatus = null;
        }
        //else find the parsed command and clear that one
        else {
            if(processedCommand != null)
                processedCommand.clear();

            CommandLineParser parsed = parsedCommand();
            if (parsed != null)
                parsed.clear();
        }
    }

    @Override
    public boolean isGroupCommand() {
        List<CommandLineParser<C>> parsers = getChildParsers();
        return parsers != null && parsers.size() > 0;
    }

    @Override
    public String toString() {
        return "AeshCommandLineParser{" +
                "processedCommand=" + processedCommand +
                ", childParsers=" + childParsers +
                ", isChild=" + isChild +
                ", lastParsedOption=" + lastParsedOption +
                ", parsedCommand=" + parsedCommand +
                ", completeStatus=" + completeStatus +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AeshCommandLineParser)) return false;

        AeshCommandLineParser that = (AeshCommandLineParser) o;

        return processedCommand.equals(that.processedCommand);

    }

    @Override
    public int hashCode() {
        return processedCommand.hashCode();
    }
}
