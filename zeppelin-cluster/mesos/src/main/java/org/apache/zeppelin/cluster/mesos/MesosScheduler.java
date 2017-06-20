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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.Status;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Value.Type;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.cluster.ClusterManager;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;

/**
 *
 */
public class MesosScheduler extends ClusterManager implements Scheduler {

  private static final Logger logger = LoggerFactory.getLogger(MesosScheduler.class);

  private final String mesosMaster;

  private ExecutorService executorService = Executors.newSingleThreadExecutor();
  private SchedulerDriver schedulerDriver;
  private SchedulerDriver driver; // Passed by registered()
  private Queue<ExecutorInfo> taskQueue;
  private List<TaskInfo> runningList;
  private Map<TaskID, TaskInfo> submittedTaskMap;
  private AtomicInteger atomicId;
  private boolean isStarted;

  public MesosScheduler(String mesosMaster) {
    this.mesosMaster = mesosMaster;

    this.taskQueue = new ArrayBlockingQueue<>(10);
    this.runningList = new ArrayList<>();
    this.submittedTaskMap = new ConcurrentHashMap<>();
    this.atomicId = new AtomicInteger(0);
    this.isStarted = false;
  }

  @Override
  public void registered(SchedulerDriver schedulerDriver, FrameworkID frameworkID,
      MasterInfo masterInfo) {
  }

  @Override
  public void reregistered(SchedulerDriver schedulerDriver, MasterInfo masterInfo) {

  }

  @Override
  public void resourceOffers(SchedulerDriver schedulerDriver, List<Offer> offers) {
    List<OfferID> acceptedOffers = new ArrayList<>();
    List<TaskInfo> submittedTasks = new ArrayList<>();
    for (Offer offer : offers) {
      logger.debug("Offer: {}", offer.getId().getValue());
      ExecutorInfo executorInfo;
      if (null != (executorInfo = taskQueue.poll())) {
        TaskID taskID = TaskID.newBuilder().setValue(String.valueOf(atomicId.getAndIncrement()))
            .build();

        TaskInfo taskInfo = TaskInfo.newBuilder().setName("interpreter task " + taskID.getValue())
            .setTaskId(taskID).setSlaveId(offer.getSlaveId()).addResources(
                Resource.newBuilder().setName("cpus").setType(Type.SCALAR)
                    .setScalar(Value.Scalar.newBuilder().setValue(1))).addResources(
                Resource.newBuilder().setName("mem").setType(Type.SCALAR)
                    .setScalar(Value.Scalar.newBuilder().setValue(1024))).setExecutor(executorInfo)
            .build();

        acceptedOffers.add(offer.getId());
        submittedTasks.add(taskInfo);
      } else {
        schedulerDriver.declineOffer(offer.getId());
      }
    }
    schedulerDriver.launchTasks(acceptedOffers, submittedTasks);
  }

  @Override
  public void offerRescinded(SchedulerDriver schedulerDriver, OfferID offerID) {

  }

  @Override
  public void statusUpdate(SchedulerDriver schedulerDriver, TaskStatus taskStatus) {
    logger.info("{}", taskStatus);
    TaskInfo taskInfo = submittedTaskMap.get(taskStatus.getTaskId());

    if (TaskState.TASK_RUNNING == taskStatus.getState()) {
      runningList.add(taskInfo);
    } else if (TaskState.TASK_FINISHED == taskStatus.getState()) {
      runningList.remove(taskInfo);
    }
  }

  @Override
  public void frameworkMessage(SchedulerDriver schedulerDriver, ExecutorID executorID,
      SlaveID slaveID, byte[] bytes) {

  }

  @Override
  public void disconnected(SchedulerDriver schedulerDriver) {

  }

  @Override
  public void slaveLost(SchedulerDriver schedulerDriver, SlaveID slaveID) {

  }

  @Override
  public void executorLost(SchedulerDriver schedulerDriver, ExecutorID executorID, SlaveID slaveID,
      int i) {

  }

  @Override
  public void error(SchedulerDriver schedulerDriver, String s) {

  }

  @Override
  public void start() {
    if (null != this.schedulerDriver || isStarted) {
      return;
    }

    FrameworkInfo frameworkInfo = FrameworkInfo.newBuilder().setUser("")
        .setName("Zeppelin Interpreter").build();
    schedulerDriver = new MesosSchedulerDriver(this, frameworkInfo, mesosMaster);
    executorService.submit(new Runnable() {
      @Override
      public void run() {
        logger.info("Mesos Scheduler starts...");
        Status status = schedulerDriver.run();
        logger.info("Mesos Scheduler stopped with {}", status);
      }
    });

    isStarted = true;
  }

  @Override
  public void stop() {
    if (isStarted) {
      schedulerDriver.stop();
      schedulerDriver = null;
      executorService.shutdown();
      executorService = Executors.newSingleThreadExecutor();
    }
  }

  @Override
  public RemoteInterpreterProcess createInterpreter(String id, String name, String groupName,
      Map<String, String> env, Properties properties, int connectTimeout,
      RemoteInterpreterProcessListener listener, ApplicationEventListener appListener,
      String homeDir, String interpreterDir) throws InterpreterException {
    if (!isStarted) {
      start();
    }
    return new RemoteInterpreterMesosProcess(connectTimeout, listener, appListener, homeDir,
        groupName, taskQueue, runningList, env);
  }

  @Override
  public void releaseResource(String id) {

  }
}
