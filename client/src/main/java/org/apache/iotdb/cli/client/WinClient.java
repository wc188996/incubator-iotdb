/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.cli.client;

import java.io.Console;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.iotdb.cli.exception.ArgsErrorException;
import org.apache.iotdb.jdbc.Config;
import org.apache.iotdb.jdbc.IoTDBConnection;
import org.apache.thrift.TException;

public class WinClient extends AbstractClient {

  private static CommandLine commandLine;

  /**
   * main function.
   *
   * @param args -console args
   */
  public static void main(String[] args) throws ClassNotFoundException, SQLException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    Options options = createOptions();
    HelpFormatter hf = new HelpFormatter();
    hf.setWidth(MAX_HELP_CONSOLE_WIDTH);
    commandLine = null;

    String[] newArgs;

    if (args == null || args.length == 0) {
      println("Require more params input, please check the following hint.");
      hf.printHelp(IOTDB_CLI_PREFIX, options, true);
      return;
    }

    init();

    newArgs = removePasswordArgs(args);

    boolean continues = parseCommandLine(options, newArgs, hf);
    if (!continues) {
      return;
    }

    serve();
  }

  private static String readPassword() {
    Console c = System.console();
    if (c == null) { // IN ECLIPSE IDE
      print(IOTDB_CLI_PREFIX + "> please input password: ");
      Scanner scanner = new Scanner(System.in);
      return scanner.nextLine();
    } else { // Outside Eclipse IDE
      return new String(c.readPassword(IOTDB_CLI_PREFIX + "> please input password: "));
    }
  }

  private static boolean parseCommandLine(Options options, String[] newArgs, HelpFormatter hf) {
    try {
      CommandLineParser parser = new DefaultParser();
      commandLine = parser.parse(options, newArgs);
      if (commandLine.hasOption(HELP_ARGS)) {
        hf.printHelp(IOTDB_CLI_PREFIX, options, true);
        return false;
      }
      if (commandLine.hasOption(ISO8601_ARGS)) {
        setTimeFormat("long");
      }
      if (commandLine.hasOption(MAX_PRINT_ROW_COUNT_ARGS)) {
        maxPrintRowCount = Integer.valueOf(commandLine.getOptionValue(MAX_PRINT_ROW_COUNT_ARGS));
        if (maxPrintRowCount < 0) {
          maxPrintRowCount = Integer.MAX_VALUE;
        }
      }
    } catch (ParseException e) {
      println("Require more params input, please check the following hint.");
      hf.printHelp(IOTDB_CLI_PREFIX, options, true);
      handleException(e);
      return false;
    } catch (NumberFormatException e) {
      println(
          IOTDB_CLI_PREFIX + "> error format of max print row count, it should be number");
      handleException(e);
      return false;
    }
    return true;
  }

  private static void serve() {
    try (Scanner scanner = new Scanner(System.in)) {
      host = checkRequiredArg(HOST_ARGS, HOST_NAME, commandLine, false, host);
      port = checkRequiredArg(PORT_ARGS, PORT_NAME, commandLine, false, port);
      username = checkRequiredArg(USERNAME_ARGS, USERNAME_NAME, commandLine, true, null);
      password = commandLine.getOptionValue(PASSWORD_ARGS);
      if (password == null) {
        password = readPassword();
      }
      receiveCommands(scanner);
    } catch (ArgsErrorException e) {
      println(IOTDB_CLI_PREFIX + "> input params error because" + e.getMessage());
      handleException(e);
    } catch (Exception e) {
      println(IOTDB_CLI_PREFIX + "> exit client with error " + e.getMessage());
      handleException(e);
    }
  }

  private static void receiveCommands(Scanner scanner) throws TException {
    try (IoTDBConnection connection = (IoTDBConnection) DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + host + ":" + port + "/", username, password)) {
      properties = connection.getServerProperties();
      AGGREGRATE_TIME_LIST.addAll(properties.getSupportedTimeAggregationOperations());
      TIMESTAMP_PRECISION = properties.getTimestampPrecision();

      displayLogo(properties.getVersion());
      println(IOTDB_CLI_PREFIX + "> login successfully");
      while (true) {
        print(IOTDB_CLI_PREFIX + "> ");
        String s = scanner.nextLine();
        boolean continues = processCommand(s, connection);
        if (!continues) {
          break;
        }
      }
    } catch (SQLException e) {
      println(String
          .format("%s> %s Host is %s, port is %s.", IOTDB_CLI_PREFIX, e.getMessage(), host,
              port));
      handleException(e);
    }
  }

  private static boolean processCommand(String s, IoTDBConnection connection) {
    if (s == null) {
      return true;
    }
    String[] cmds = s.trim().split(";");
    for (int i = 0; i < cmds.length; i++) {
      String cmd = cmds[i];
      if (cmd != null && !"".equals(cmd.trim())) {
        OperationResult result = handleInputCmd(cmd, connection);
        switch (result) {
          case STOP_OPER:
            return false;
          case CONTINUE_OPER:
            continue;
          default:
            break;
        }
      }
    }
    return true;
  }
}
