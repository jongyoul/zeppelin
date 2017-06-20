/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.cluster.mesos;

import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.Environment;
import org.apache.mesos.Protos.Environment.Variable;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.CallbackInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterCallbackService;

/**
 *
 */
public class RemoteInterpreterMesosProcess extends RemoteInterpreterProcess {

  private static final Logger logger = LoggerFactory.getLogger(RemoteInterpreterMesosProcess.class);

  private final String homeDir;
  private final String groupName;
  private final Queue<ExecutorInfo> taskQueue;
  private final List<TaskInfo> runningList;
  private final Map<String, String> envs;

  private String host;
  private int port;

  private TServer callbackServer;
  private CountDownLatch hostPortLatch;
  private boolean isStarted;

  public RemoteInterpreterMesosProcess(int connectTimeout,
    RemoteInterpreterProcessListener listener,
    ApplicationEventListener appListener, String homeDir, String groupName,
    Queue<ExecutorInfo> taskQueue, List<TaskInfo> runningList, Map<String, String> envs) {
    super(connectTimeout, listener, appListener);
    this.homeDir = homeDir;
    this.groupName = groupName;
    this.taskQueue = taskQueue;
    this.runningList = runningList;
    this.envs = envs;

    this.host = null;
    this.port = -1;
    this.hostPortLatch = new CountDownLatch(1);
    this.isStarted = false;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void start(String userName, Boolean isUserImpersonate) {
    try {
      String callbackHost = RemoteInterpreterUtils.findAvailableHostname();
      int callbackPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();

      logger.info("Thrift server for callback will start. Port: {}", callbackPort);
      try {
        callbackServer = new TThreadPoolServer(
          new TThreadPoolServer.Args(new TServerSocket(callbackPort)).processor(
            new RemoteInterpreterCallbackService.Processor<>(
              new RemoteInterpreterCallbackService.Iface() {
                @Override
                public void callback(CallbackInfo callbackInfo) throws TException {
                  logger.info("Registered: {}", callbackInfo);
                  host = callbackInfo.getHost();
                  port = callbackInfo.getPort();
                  hostPortLatch.countDown();
                }
              })));
        // Start thrift server to receive callbackInfo from RemoteInterpreterServer;
        new Thread(new Runnable() {
          @Override
          public void run() {
            callbackServer.serve();
          }
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
          @Override
          public void run() {
            if (callbackServer.isServing()) {
              callbackServer.stop();
            }
          }
        }));

        while (!callbackServer.isServing()) {
          logger.debug("callbackServer is not serving");
          Thread.sleep(500);
        }
        logger.debug("callbackServer is serving now");
      } catch (TTransportException e) {
        logger.error("callback server error.", e);
      } catch (InterruptedException e) {
        logger.warn("", e);
      }

      String classpath =
        makeCpWithAsterisk(homeDir, "lib", "interpreter") + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "lib", "cluster", "common") + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "lib", "cluster", "mesos") + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "interpreter", groupName) + File.pathSeparator
          + makeCp(homeDir, "zeppelin-interpreter", "target", "classes") + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "zeppelin-interpreter", "target") + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "zeppelin-interpreter", "target", "lib")
          + File.pathSeparator
          + makeCp(homeDir, "zeppelin-cluster", "common", "target", "classes")
          + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "zeppelin-cluster", "common", "target")
          + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "zeppelin-cluster", "common", "target", "lib")
          + File.pathSeparator
          + makeCp(homeDir, "zeppelin-cluster", "mesos", "target", "classes")
          + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "zeppelin-cluster", "mesos", "target")
          + File.pathSeparator
          + makeCpWithAsterisk(homeDir, "zeppelin-cluster", "mesos", "target", "lib");

      String command = String
        .format("cd %s;bin/interpreter.sh -d %s/interpreter/%s -c %s -p %d -g %s -r %s", homeDir,
          homeDir, groupName, callbackHost, callbackPort, groupName,
          MesosRemoteInterpreterServer.class.getName());

      /*CommandLine cmdLine = CommandLine.parse(interpreterRunner);
      cmdLine.addArgument("-d", false);
      cmdLine.addArgument(interpreterDir, false);
      cmdLine.addArgument("-c", false);
      cmdLine.addArgument(callbackHost, false);
      cmdLine.addArgument("-p", false);
      cmdLine.addArgument(Integer.toString(callbackPort), false);
      if (isUserImpersonate && !userName.equals("anonymous")) {
        cmdLine.addArgument("-u", false);
        cmdLine.addArgument(userName, false);
      }
      cmdLine.addArgument("-l", false);
      cmdLine.addArgument(localRepoDir, false);
      cmdLine.addArgument("-g", false);
      cmdLine.addArgument(interpreterGroupName, false);*/

      logger.debug("command: {}", command);

      Environment.Builder envBuilder = Environment.newBuilder();

      for (Map.Entry<String, String> entry : envs.entrySet()) {
        logger.debug("envs. key: {}, value: {}", entry.getKey(), entry.getValue());
        envBuilder
          .addVariables(Variable.newBuilder().setName(entry.getKey()).setValue(entry.getValue()));
      }

      CommandInfo commandInfo = CommandInfo.newBuilder().setValue(command)
        .setEnvironment(envBuilder).build();

      ExecutorInfo executorInfo = ExecutorInfo.newBuilder()
        .setExecutorId(ExecutorID.newBuilder().setValue(groupName + " Interpreter"))
        .setCommand(commandInfo)
        .setName(groupName).setSource("java").build();

      taskQueue.add(executorInfo);

      /*boolean waitUntilRunning = true;
      while (waitUntilRunning) {
        logger.info("Waiting until submitted and run interpreter from mesos");
        for (TaskInfo taskInfo : runningList) {
          if (taskInfo.getExecutor().equals(executorInfo)) {
            waitUntilRunning = false;
            break;
          }
        }
        Thread.sleep(1000);
      }
      logger.info("Interpreter {} is running...", groupName);*/

      hostPortLatch.await(getConnectTimeout(), TimeUnit.MILLISECONDS);
      // Check if not running
      if (null == host || -1 == port) {
        hostPortLatch = new CountDownLatch(1);
        callbackServer.stop();
        throw new InterpreterException("Cannot run interpreter");
      }

    } catch (InterruptedException | IOException e) {
      throw new InterpreterException(e);
    } catch (InterpreterException e) {
      throw e;
    }


  }

  @Override
  public void stop() {

  }

  @Override
  public boolean isRunning() {
    return false;
  }

  /**
   * For Jars
   */
  private String makeCpWithAsterisk(String... dirs) {
    return makeCp(dirs).concat(File.separator).concat("*");
  }

  /**
   * For classes
   */
  private String makeCp(String... dirs) {
    return Joiner.on(File.separator).join(dirs);
  }
}
