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
package org.aesh.cl;

import org.aesh.command.impl.activator.AeshCommandActivatorProvider;
import org.aesh.command.impl.activator.AeshOptionActivatorProvider;
import org.aesh.command.impl.completer.AeshCompleterInvocationProvider;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.impl.converter.AeshConverterInvocationProvider;
import org.aesh.command.impl.invocation.AeshInvocationProviders;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.CommandDefinition;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.impl.validator.AeshValidatorInvocationProvider;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.InvocationProviders;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;
import org.aesh.console.AeshContext;
import org.aesh.console.settings.SettingsBuilder;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import org.aesh.command.CommandException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CommandLineParserTest {

    private final InvocationProviders invocationProviders = new AeshInvocationProviders(
            SettingsBuilder.builder()
                    .converterInvocationProvider(new AeshConverterInvocationProvider())
                    .completerInvocationProvider(new AeshCompleterInvocationProvider())
                    .validatorInvocationProvider(new AeshValidatorInvocationProvider())
                    .optionActivatorProvider(new AeshOptionActivatorProvider())
                    .commandActivatorProvider(new AeshCommandActivatorProvider()).build());

    @Test
    public void testParseCommandLine1() throws Exception {

        AeshContext aeshContext = SettingsBuilder.builder().build().aeshContext();
        CommandLineParser<Parser1Test> parser = new AeshCommandContainerBuilder<Parser1Test>().create(Parser1Test.class).getParser();

        parser.populateObject("test -f -e bar -Df=g /tmp/file.txt", invocationProviders, aeshContext, true);
        Parser1Test p1 = parser.getCommand();

        assertTrue(p1.foo);
        assertEquals("bar", p1.equal);
        assertEquals("/tmp/file.txt", p1.arguments.get(0));

        parser.populateObject("test -f -e=bar -Df=g /tmp/file.txt", invocationProviders, aeshContext, true);
        assertTrue(p1.foo);
        assertEquals("bar", p1.equal);
        assertEquals("/tmp/file.txt", p1.arguments.get(0));
        assertEquals("g", p1.define.get("f"));

        parser.populateObject("test -Dg=f /tmp/file.txt -e=bar foo bar", invocationProviders, aeshContext, true);
        assertFalse(p1.foo);
        assertEquals("f", p1.define.get("g"));
        assertEquals("bar", p1.equal);
        assertEquals("/tmp/file.txt", p1.arguments.get(0));
        assertEquals("foo", p1.arguments.get(1));
        assertEquals("bar", p1.arguments.get(2));

        parser.populateObject("test -e beer -DXms=128m -DXmx=512m --X /tmp/file.txt", invocationProviders, aeshContext, true);
        assertEquals("beer", p1.equal);
        assertEquals("/tmp/file.txt", p1.arguments.get(0));
        assertTrue(p1.enableX);

        assertEquals("128m", p1.define.get("Xms"));
        assertEquals("512m", p1.define.get("Xmx"));

        parser.populateObject("test --equal \"bar bar2\" -DXms=\"128g \" -DXmx=512g\\ m /tmp/file.txt", invocationProviders, aeshContext, true);
        assertEquals("bar bar2", p1.equal);

        assertEquals("128g ", p1.define.get("Xms"));
        assertEquals("512g m", p1.define.get("Xmx"));

        parser.populateObject("test -fX -e bar -Df=g /tmp/file.txt\\ ", invocationProviders, aeshContext, true);
        assertTrue(p1.foo);
        assertTrue(p1.enableX);
        assertEquals("bar", p1.equal);
        assertEquals("g", p1.define.get("f"));
        assertEquals("/tmp/file.txt ", p1.arguments.get(0));
    }

    @Test
    public void testParseCommandLine2() throws Exception {

        AeshContext aeshContext = SettingsBuilder.builder().build().aeshContext();
        CommandLineParser<Parser2Test> parser = new AeshCommandContainerBuilder<Parser2Test>().create(Parser2Test.class).getParser();
        Parser2Test p2 = parser.getCommand();

        parser.populateObject("test -d true --bar Foo.class", invocationProviders, aeshContext, true);
        assertEquals("true", p2.display);
        assertNull(p2.version);
        assertEquals("Foo.class", p2.bar);
        assertNull(p2.arguments);

        parser.populateObject("test -V verbose -d false -b com.bar.Bar.class /tmp/file\\ foo.txt /tmp/bah.txt", invocationProviders, aeshContext, true);
        assertEquals("verbose", p2.version);
        assertEquals("false", p2.display);
        assertEquals("com.bar.Bar.class", p2.bar);
        assertEquals("/tmp/file foo.txt", p2.arguments.get(0));
        assertEquals("/tmp/bah.txt", p2.arguments.get(1));

        assertTrue(parser.getProcessedCommand().parserExceptions().isEmpty());
    }

    @Test
    public void testParseGroupCommand() throws Exception {

        AeshContext aeshContext = SettingsBuilder.builder().build().aeshContext();
        CommandLineParser parser = new AeshCommandContainerBuilder().create(GroupCommandTest.class).getParser();
        GroupCommandTest g1 = (GroupCommandTest) parser.getCommand();
        ChildTest1 c1 = (ChildTest1) parser.getChildParser("child1").getCommand();

        parser.populateObject("group child1 --foo BAR", invocationProviders, aeshContext, true);
        assertEquals("BAR", c1.foo);

        parser.populateObject("group child1 --foo BAR --bar FOO", invocationProviders, aeshContext, true);
        assertEquals("BAR", c1.foo);
        assertEquals("FOO", c1.bar);
    }

    @Test
    public void testParseCommandLine4() throws Exception {
        AeshContext aeshContext = SettingsBuilder.builder().build().aeshContext();
        CommandLineParser<Parser4Test> parser = new AeshCommandContainerBuilder<Parser4Test>().create(Parser4Test.class).getParser();
        Parser4Test p4 = parser.getCommand();

        parser.populateObject("test -o bar1,bar2,bar3 foo", invocationProviders, aeshContext, true);
        assertEquals("bar1", p4.option.get(0));
        assertEquals("bar3", p4.option.get(2));
        assertEquals(3, p4.option.size());

        parser.populateObject("test -o=bar1,bar2,bar3 foo", invocationProviders, aeshContext, true);
        assertEquals("bar1", p4.option.get(0));
        assertEquals("bar3", p4.option.get(2));
        assertEquals(3, p4.option.size());
        assertEquals("foo", p4.arguments.get(0));

        parser.populateObject("test --option=bar1,bar2,bar3 foo", invocationProviders, aeshContext, true);
        assertEquals("bar1", p4.option.get(0));
        assertEquals("bar3", p4.option.get(2));
        assertEquals(3, p4.option.size());
        assertEquals("foo", p4.arguments.get(0));

        parser.populateObject("test --help bar4:bar5:bar6 foo", invocationProviders, aeshContext, true);
        assertEquals("bar4", p4.help.get(0));
        assertEquals("bar6", p4.help.get(2));

        parser.populateObject("test --help2 bar4 bar5 bar6", invocationProviders, aeshContext, true);
        assertEquals("bar4", p4.help2.get(0));
        assertEquals("bar6", p4.help2.get(2));

        parser.populateObject("test --bar 1,2,3", invocationProviders, aeshContext, true);
        assertEquals(new Integer(1), p4.bar.get(0));
        assertEquals(new Integer(2), p4.bar.get(1));
        assertEquals(new Integer(3), p4.bar.get(2));
    }

    @Test
    public void testParseCommandLine5() throws Exception {
        AeshContext aeshContext = SettingsBuilder.builder().build().aeshContext();
        CommandLineParser<Parser5Test> parser = new AeshCommandContainerBuilder<Parser5Test>().create(Parser5Test.class).getParser();
        Parser5Test p5 = parser.getCommand();

        parser.populateObject("test  --foo  \"-X1 X2 -X3\" --baz -wrong --bar -q \"-X4 -X5\"", invocationProviders, aeshContext, true);
        assertEquals("-X1", p5.foo.get(0));
        assertEquals("X2", p5.foo.get(1));
        assertEquals("-X3", p5.foo.get(2));
        assertTrue(p5.baz);
        assertTrue(p5.bar);
        assertEquals(2, p5.qux.size());
        assertEquals("-X4", p5.qux.get(0));
        assertEquals("-X5", p5.qux.get(1));


        parser.populateObject("test  --foo  -X1 X2 -X3 --baz -wrong --bar -q -X4 -X5", invocationProviders, aeshContext, true);
        assertEquals("-X1", p5.foo.get(0));
        assertEquals("X2", p5.foo.get(1));
        assertEquals("-X3", p5.foo.get(2));
        assertTrue(p5.baz);
        assertTrue(p5.bar);
        assertEquals(2, p5.qux.size());
        assertEquals("-X4", p5.qux.get(0));
        assertEquals("-X5", p5.qux.get(1));
    }

    @Test
    public void testSubClass() throws Exception {
        AeshContext aeshContext = SettingsBuilder.builder().build().aeshContext();
        CommandLineParser<SubHelp> parser = new AeshCommandContainerBuilder<SubHelp>().create(SubHelp.class).getParser();
        SubHelp subHelp = parser.getCommand();

        parser.populateObject("subhelp --foo bar -h", invocationProviders, aeshContext, true);
        assertEquals("bar", subHelp.foo);
        assertTrue(subHelp.getHelp());
    }

    @CommandDefinition(name = "test", description = "a simple test", aliases = {"toto"})
    public class Parser1Test extends TestingCommand {

        @Option(shortName = 'X', name = "X", description = "enable X", hasValue = false)
        private Boolean enableX;

        @Option(shortName = 'f', name = "foo", description = "enable foo", hasValue = false)
        private Boolean foo;

        @Option(shortName = 'e', name = "equal", description = "enable equal", required = true)
        private String equal;

        @OptionGroup(shortName = 'D', description = "define properties", required = true)
        private Map<String,String> define;

        @Arguments
        private List<String> arguments;
    }

    @CommandDefinition(name = "test", description = "more [options] file...")
    public class Parser2Test extends TestingCommand {
        @Option(shortName = 'd', name = "display", description = "display help instead of ring bell")
        private String display;

        @Option(shortName = 'b', name = "bar", argument = "classname", required = true, description = "bar bar")
        private String bar;

        @Option(shortName = 'V', name = "version", description = "output version information and exit")
        private String version;

        @Arguments
        private List<String> arguments;
    }

    @CommandDefinition(name = "test", description = "this is a command without options")
    public class Parser3Test extends TestingCommand {}

    @CommandDefinition(name = "test", description = "testing multiple values")
    public class Parser4Test  extends TestingCommand{
        @OptionList(shortName = 'o', name="option", valueSeparator = ',')
        private List<String> option;

        @OptionList
        private List<Integer> bar;

        @OptionList(shortName = 'h', valueSeparator = ':')
        private List<String> help;

        @OptionList(shortName = 'e', valueSeparator = ' ')
        private List<String> help2;

        @Arguments
        private List<String> arguments;
    }

    @CommandDefinition(name = "test", description = "testing multiple values")
    public class Parser5Test  extends TestingCommand{
        @OptionList(shortName = 'f', name="foo", valueSeparator=' ')
        private List<String> foo;

        @Option(shortName = 'b', name="bar", hasValue = false)
        private Boolean bar;

        @Option(shortName = 'z', name="baz", hasValue = false)
        private Boolean baz;

        @OptionList(shortName = 'q', name="qux", valueSeparator=' ')
        private List<String> qux;

        @Arguments
        private List<String> arguments;
    }

    @CommandDefinition(name = "child1", description = "")
    public class ChildTest1 extends TestingCommand {

        @Option
        private String foo;

        @Option
        private String bar;

    }

    @GroupCommandDefinition(name = "group", description = "", groupCommands = {ChildTest1.class})
    public class GroupCommandTest extends TestingCommand {

        @Option(hasValue = false)
        private boolean help;

    }

    public class TestingCommand implements Command {
        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
            return CommandResult.SUCCESS;
        }
    }


    public class HelpClass {

        @Option(name = "help", shortName = 'h', hasValue = false)
        private boolean help;

        public boolean getHelp() {
            return help;
        }

    }

    @CommandDefinition(name = "subhelp", description = "")
    public class SubHelp extends HelpClass implements Command {

        @Option
        private String foo;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
            return CommandResult.SUCCESS;
        }

    }
}