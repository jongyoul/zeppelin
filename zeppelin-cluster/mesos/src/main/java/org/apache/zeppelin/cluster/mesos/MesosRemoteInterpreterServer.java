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

import java.io.IOException;
import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.MesosExecutorDriver;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.SlaveInfo;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;

/**
 *
 */
public class MesosRemoteInterpreterServer extends RemoteInterpreterServer implements Executor {

  private static final Logger logger = LoggerFactory.getLogger(MesosRemoteInterpreterServer.class);

  private ExecutorDriver executorDriver;
  private TaskInfo taskInfo;

  public MesosRemoteInterpreterServer(String callbackHost, int port)
      throws TTransportException, IOException {
    super(callbackHost, port);
  }

  @Override
  public void run() {
    super.run();
  }

  @Override
  public void close(String sessionKey, String className) throws TException {
    executorDriver.sendStatusUpdate(
        TaskStatus.newBuilder().setTaskId(taskInfo.getTaskId()).setState(TaskState.TASK_FINISHED)
            .build());
    executorDriver.stop();
  }

  public static void main(String[] args) throws Exception {
    logger.info("Starting...");
    String callbackHost = null;
    int port = Constants.ZEPPELIN_INTERPRETER_DEFAUlT_PORT;
    if (args.length > 0) {
      callbackHost = args[0];
      port = Integer.parseInt(args[1]);
    }
    MesosRemoteInterpreterServer mesosRemoteInterpreterServer =
        new MesosRemoteInterpreterServer(callbackHost, port);

    final ExecutorDriver executorDriver = new MesosExecutorDriver(mesosRemoteInterpreterServer);
    new Thread(new Runnable() {
      @Override
      public void run() {
        logger.info("Driver will run");
        logger.info("Executor driver stopped with {}", executorDriver.run());
      }
    }).start();
    mesosRemoteInterpreterServer.start();
    mesosRemoteInterpreterServer.join();
    System.exit(0);
  }

  @Override
  public void registered(ExecutorDriver executorDriver, ExecutorInfo executorInfo,
      FrameworkInfo frameworkInfo, SlaveInfo slaveInfo) {
    logger.info("registered");
  }

  @Override
  public void reregistered(ExecutorDriver executorDriver, SlaveInfo slaveInfo) {
    logger.info("reregistered");
  }

  @Override
  public void disconnected(ExecutorDriver executorDriver) {
    logger.info("disconnected");
  }

  @Override
  public void launchTask(ExecutorDriver executorDriver, TaskInfo taskInfo) {
    this.executorDriver = executorDriver;
    this.taskInfo = taskInfo;
    logger.info("launchTask");
    executorDriver.sendStatusUpdate(
        TaskStatus.newBuilder().setTaskId(taskInfo.getTaskId()).setState(TaskState.TASK_RUNNING)
            .build());
  }

  @Override
  public void killTask(ExecutorDriver executorDriver, TaskID taskID) {
    logger.info("killTask");
  }

  @Override
  public void frameworkMessage(ExecutorDriver executorDriver, byte[] bytes) {

  }

  @Override
  public void shutdown(ExecutorDriver executorDriver) {

  }

  @Override
  public void error(ExecutorDriver executorDriver, String s) {
    logger.info("error: {}", s);
  }
}
