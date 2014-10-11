package com.nirmata.workflow;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.nirmata.workflow.admin.RunInfo;
import com.nirmata.workflow.admin.TaskInfo;
import com.nirmata.workflow.executor.TaskExecutionStatus;
import com.nirmata.workflow.executor.TaskExecutor;
import com.nirmata.workflow.models.RunId;
import com.nirmata.workflow.models.Task;
import com.nirmata.workflow.models.TaskExecutionResult;
import com.nirmata.workflow.models.TaskId;
import com.nirmata.workflow.models.TaskType;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestAdmin extends BaseForTests
{
    @Test
    public void testTaskInfo() throws Exception
    {
        TaskType taskType = new TaskType("test", "1", true);
        Task task1 = new Task(new TaskId(), taskType);
        Task task2 = new Task(new TaskId(), taskType);
        Task root = new Task(new TaskId(), Lists.newArrayList(task1, task2));

        CountDownLatch startedLatch = new CountDownLatch(2);
        CountDownLatch waitLatch = new CountDownLatch(1);
        TaskExecutor taskExecutor = (manager, task) -> () -> {
            startedLatch.countDown();
            if ( task.getTaskId().equals(task2.getTaskId()) )
            {
                try
                {
                    waitLatch.await();
                }
                catch ( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            Map<String, String> resultData = Maps.newHashMap();
            resultData.put("taskId", task.getTaskId().getId());
            return new TaskExecutionResult(TaskExecutionStatus.SUCCESS, "", resultData);
        };
        WorkflowManager workflowManager = WorkflowManagerBuilder.builder()
            .addingTaskExecutor(taskExecutor, 10, taskType)
            .withCurator(curator, "test", "1")
            .build();
        try
        {
            workflowManager.start();

            RunId runId = workflowManager.submitTask(root);

            Timing timing = new Timing();
            Assert.assertTrue(timing.awaitLatch(startedLatch));

            timing.sleepABit();

            Map<TaskId, TaskInfo> taskInfos = workflowManager.getAdmin().getTaskInfo(runId).stream().collect(Collectors.toMap(TaskInfo::getTaskId, Function.identity()));
            Assert.assertEquals(taskInfos.size(), 2);
            Assert.assertTrue(taskInfos.containsKey(task1.getTaskId()));
            Assert.assertTrue(taskInfos.containsKey(task2.getTaskId()));
            Assert.assertTrue(taskInfos.get(task1.getTaskId()).isComplete());
            Assert.assertFalse(taskInfos.get(task2.getTaskId()).isComplete());
            Assert.assertEquals(taskInfos.get(task1.getTaskId()).getResult().getResultData().get("taskId"), task1.getTaskId().getId());

            waitLatch.countDown();
            timing.sleepABit();

            taskInfos = workflowManager.getAdmin().getTaskInfo(runId).stream().collect(Collectors.toMap(TaskInfo::getTaskId, Function.identity()));
            Assert.assertEquals(taskInfos.size(), 2);
            Assert.assertTrue(taskInfos.containsKey(task1.getTaskId()));
            Assert.assertTrue(taskInfos.containsKey(task2.getTaskId()));
            Assert.assertTrue(taskInfos.get(task1.getTaskId()).isComplete());
            Assert.assertTrue(taskInfos.get(task2.getTaskId()).isComplete());
            Assert.assertEquals(taskInfos.get(task1.getTaskId()).getResult().getResultData().get("taskId"), task1.getTaskId().getId());
            Assert.assertEquals(taskInfos.get(task2.getTaskId()).getResult().getResultData().get("taskId"), task2.getTaskId().getId());
        }
        finally
        {
            CloseableUtils.closeQuietly(workflowManager);
        }
    }

    @Test
    public void testRunInfo() throws Exception
    {
        TaskType taskType = new TaskType("test", "1", true);
        Task task1 = new Task(new TaskId(), taskType);
        Task task2 = new Task(new TaskId(), taskType);

        CountDownLatch startedLatch = new CountDownLatch(2);
        CountDownLatch waitLatch = new CountDownLatch(1);
        TaskExecutor taskExecutor = (manager, task) -> () -> {
            startedLatch.countDown();
            if ( task.getTaskId().equals(task2.getTaskId()) )
            {
                try
                {
                    waitLatch.await();
                }
                catch ( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return new TaskExecutionResult(TaskExecutionStatus.SUCCESS, "");
        };
        WorkflowManager workflowManager = WorkflowManagerBuilder.builder()
            .addingTaskExecutor(taskExecutor, 10, taskType)
            .withCurator(curator, "test", "1")
            .build();
        try
        {
            workflowManager.start();

            RunId runId1 = workflowManager.submitTask(task1);
            RunId runId2 = workflowManager.submitTask(task2);

            Timing timing = new Timing();
            Assert.assertTrue(timing.awaitLatch(startedLatch));

            timing.sleepABit();

            Map<RunId, RunInfo> runs = workflowManager.getAdmin().getRunInfo().stream().collect(Collectors.toMap(RunInfo::getRunId, Function.identity()));
            Assert.assertEquals(runs.size(), 2);
            Assert.assertTrue(runs.containsKey(runId1));
            Assert.assertTrue(runs.containsKey(runId2));
            Assert.assertTrue(runs.get(runId1).isComplete());
            Assert.assertFalse(runs.get(runId2).isComplete());

            waitLatch.countDown();
            timing.sleepABit();

            runs = workflowManager.getAdmin().getRunInfo().stream().collect(Collectors.toMap(RunInfo::getRunId, Function.identity()));
            Assert.assertEquals(runs.size(), 2);
            Assert.assertTrue(runs.containsKey(runId1));
            Assert.assertTrue(runs.containsKey(runId2));
            Assert.assertTrue(runs.get(runId1).isComplete());
            Assert.assertTrue(runs.get(runId2).isComplete());
        }
        finally
        {
            CloseableUtils.closeQuietly(workflowManager);
        }
    }
}
