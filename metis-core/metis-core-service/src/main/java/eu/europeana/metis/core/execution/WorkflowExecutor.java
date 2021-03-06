package eu.europeana.metis.core.execution;

import eu.europeana.cloud.client.dps.rest.DpsClient;
import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.metis.core.dao.WorkflowExecutionDao;
import eu.europeana.metis.core.workflow.WorkflowExecution;
import eu.europeana.metis.core.workflow.WorkflowStatus;
import eu.europeana.metis.core.workflow.plugins.AbstractMetisPlugin;
import eu.europeana.metis.core.workflow.plugins.ExecutionProgress;
import eu.europeana.metis.core.workflow.plugins.PluginStatus;
import eu.europeana.metis.exception.ExternalTaskException;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a {@link Callable} class that accepts a {@link WorkflowExecution}. It starts that
 * WorkflowExecution given to it and will continue monitoring and updating its progress until it
 * ends either by user interaction or by the end of the Workflow. When the WorkflowExecution is
 * received there is a chance that the execution is already being handled from another
 * WorkflowExecutor in another instance and if that is the case the WorkflowExecution will be
 * dropped.
 *
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-05-29
 */
public class WorkflowExecutor implements Callable<WorkflowExecution> {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowExecutor.class);
  private static final int MONITOR_ITERATIONS_TO_FAKE = 2;
  private static final int FAKE_RECORDS_PER_ITERATION = 100;
  private static final int MAX_CANCEL_OR_MONITOR_FAILURES = 3;

  private final String workflowExecutionId;
  private final WorkflowExecutionMonitor workflowExecutionMonitor;
  private final WorkflowExecutionDao workflowExecutionDao;
  private final int monitorCheckIntervalInSecs;
  private final long periodOfNoProcessedRecordsChangeInSeconds;
  private final DpsClient dpsClient;
  private final String ecloudBaseUrl;
  private final String ecloudProvider;

  private WorkflowExecution workflowExecution;

  WorkflowExecutor(String workflowExecutionId, PersistenceProvider persistenceProvider,
      WorkflowExecutionSettings workflowExecutionSettings,
      WorkflowExecutionMonitor workflowExecutionMonitor) {
    this.workflowExecutionId = workflowExecutionId;
    this.workflowExecutionDao = persistenceProvider.getWorkflowExecutionDao();
    this.dpsClient = persistenceProvider.getDpsClient();
    this.monitorCheckIntervalInSecs = workflowExecutionSettings.getDpsMonitorCheckIntervalInSecs();
    this.periodOfNoProcessedRecordsChangeInSeconds = TimeUnit.MINUTES
        .toSeconds(workflowExecutionSettings.getPeriodOfNoProcessedRecordsChangeInMinutes());
    this.ecloudBaseUrl = workflowExecutionSettings.getEcloudBaseUrl();
    this.ecloudProvider = workflowExecutionSettings.getEcloudProvider();
    this.workflowExecutionMonitor = workflowExecutionMonitor;
  }

  @Override
  public WorkflowExecution call() {
    try {
      return callInternal();
    } catch (RuntimeException e) {
      LOGGER.warn("Exception occurred in workflow executor", e);
      throw e;
    }
  }

  private WorkflowExecution callInternal() {

    // Claim the execution: if this claim is denied, we stop this execution.
    LOGGER.info("Claiming workflow execution with id: {}", workflowExecutionId);
    this.workflowExecution = workflowExecutionMonitor.claimExecution(this.workflowExecutionId);
    if (this.workflowExecution == null) {
      LOGGER.info("Discarding WorkflowExecution with id: {}, it could not be claimed.",
          workflowExecutionId);
      return null;
    }

    // Perform the work - run the workflow.
    LOGGER.info("Starting user workflow execution with id: {} and priority {}",
        workflowExecution.getId(), workflowExecution.getWorkflowPriority());
    final Date finishDate = runInqueueOrRunningStateWorkflowExecution();

    // Process the results
    if (finishDate == null && workflowExecutionDao.isCancelling(workflowExecution.getId())) {
      // If the workflow was cancelled before it had the chance to finish, we cancel all remaining
      // plugins.
      workflowExecution.setAllRunningAndInqueuePluginsToCancelled();
      LOGGER.info("Cancelled running user workflow execution with id: {}",
          workflowExecution.getId());
    } else if (finishDate == null) {
      // So something went wrong: one plugin must have failed.
      workflowExecution.checkAndSetAllRunningAndInqueuePluginsToCancelledIfOnePluginHasFailed();
    } else {
      // If the workflow finished successfully, we record this.
      workflowExecution.setFinishedDate(finishDate);
      workflowExecution.setWorkflowStatus(WorkflowStatus.FINISHED);
      workflowExecution.setCancelling(false);
      LOGGER.info("Finished user workflow execution with id: {}", workflowExecution.getId());
    }

    // The only full update is used here. The rest of the execution uses partial updates to avoid
    // losing the cancelling state field
    workflowExecutionDao.update(workflowExecution);
    return workflowExecution;
  }

  /**
   * Will determine from which plugin of the workflow to start execution from and will iterate
   * through the plugins of the workflow one by one.
   *
   * @return The date the full workflow finished (or null if it did not finish successfully).
   */
  private Date runInqueueOrRunningStateWorkflowExecution() {

    // Find the first plugin to continue execution from
    int firstPluginPositionToStart = 0;
    for (int i = 0; i < workflowExecution.getMetisPlugins().size(); i++) {
      AbstractMetisPlugin metisPlugin = workflowExecution.getMetisPlugins().get(i);
      if (metisPlugin.getPluginStatus() == PluginStatus.INQUEUE
          || metisPlugin.getPluginStatus() == PluginStatus.RUNNING
          || metisPlugin.getPluginStatus() == PluginStatus.CLEANING) {
        firstPluginPositionToStart = i;
        break;
      }
    }

    // One by one start the plugins of the workflow
    AbstractMetisPlugin previousMetisPlugin = null;
    for (int i = firstPluginPositionToStart; i < workflowExecution.getMetisPlugins().size(); i++) {
      AbstractMetisPlugin metisPlugin = workflowExecution.getMetisPlugins().get(i);
      final Date startDateToUse = i == 0 ? workflowExecution.getStartedDate() : new Date();
      runMetisPlugin(previousMetisPlugin, metisPlugin, startDateToUse);
      if ((workflowExecutionDao.isCancelling(workflowExecution.getId())
          && metisPlugin.getFinishedDate() == null)
          || metisPlugin.getPluginStatus() == PluginStatus.FAILED) {
        break;
      }
      previousMetisPlugin = metisPlugin;
    }

    // Compute the finished date
    final AbstractMetisPlugin lastPlugin =
        workflowExecution.getMetisPlugins().get(workflowExecution.getMetisPlugins().size() - 1);
    final Date finishDate;
    if (lastPlugin.getPluginStatus() == PluginStatus.FINISHED) {
      finishDate = lastPlugin.getFinishedDate();
    } else {
      finishDate = null;
    }
    return finishDate;
  }

  /**
   * It will prepare the plugin, request the external execution and will periodically monitor,
   * update the plugin's progress and at the end finalize the plugin's status and finished date.
   *
   * @param previousAbstractMetisPlugin the plugin that was ran before the current plugin if any
   * @param abstractMetisPlugin the current plugin to run
   * @param startDateToUse The date that should be used as start date (if the plugin is not already
   * running).
   */
  private void runMetisPlugin(AbstractMetisPlugin previousAbstractMetisPlugin,
      AbstractMetisPlugin abstractMetisPlugin, Date startDateToUse) {

    // Set previous plugin revision information
    if (previousAbstractMetisPlugin != null) {
      abstractMetisPlugin.getPluginMetadata()
          .setPreviousRevisionInformation(previousAbstractMetisPlugin);
    }

    // Start execution
    if (StringUtils.isEmpty(abstractMetisPlugin.getExternalTaskId())) {
      try {
        if (abstractMetisPlugin.getPluginStatus() == PluginStatus.INQUEUE) {
          abstractMetisPlugin.setStartedDate(startDateToUse);
        }
        abstractMetisPlugin.execute(dpsClient, ecloudBaseUrl, ecloudProvider,
            workflowExecution.getEcloudDatasetId());
      } catch (ExternalTaskException | RuntimeException e) {
        LOGGER.warn("Execution of external task failed", e);
        abstractMetisPlugin.setFinishedDate(null);
        abstractMetisPlugin.setPluginStatus(PluginStatus.FAILED);
        return;
      } finally {
        workflowExecutionDao.updateWorkflowPlugins(workflowExecution);
      }
    }

    // Start periodical check and wait for plugin to be done
    long sleepTime = TimeUnit.SECONDS.toMillis(monitorCheckIntervalInSecs);
    if (abstractMetisPlugin.getPluginMetadata().isMocked()) {
      periodicCheckingLoopMocked(sleepTime, abstractMetisPlugin);
    } else {
      periodicCheckingLoop(sleepTime, abstractMetisPlugin);
    }
  }

  private void periodicCheckingLoop(long sleepTime, AbstractMetisPlugin abstractMetisPlugin) {
    TaskState taskState = null;
    int consecutiveCancelOrMonitorFailures = 0;
    boolean externalCancelCallSent = false;
    AtomicInteger previousProcessedRecords = new AtomicInteger(0);
    AtomicLong checkPointDateOfProcessedRecordsPeriodInMillis = new AtomicLong(
        System.currentTimeMillis());
    do {
      try {
        Thread.sleep(sleepTime);
        if (!externalCancelCallSent && shouldPluginBeCancelled(abstractMetisPlugin,
            previousProcessedRecords, checkPointDateOfProcessedRecordsPeriodInMillis)) {
          abstractMetisPlugin.cancel(dpsClient);
          externalCancelCallSent = true;
        }
        taskState = abstractMetisPlugin.monitor(dpsClient).getStatus();
        consecutiveCancelOrMonitorFailures = 0;
        Date updatedDate = new Date();
        abstractMetisPlugin.setUpdatedDate(updatedDate);
        abstractMetisPlugin.setPluginStatus(
            taskState == TaskState.REMOVING_FROM_SOLR_AND_MONGO ? PluginStatus.CLEANING
                : PluginStatus.RUNNING);
        workflowExecution.setUpdatedDate(updatedDate);
        workflowExecutionDao.updateMonitorInformation(workflowExecution);
      } catch (InterruptedException e) {
        LOGGER.warn("Thread was interrupted during monitoring of external task", e);
        Thread.currentThread().interrupt();
        return;
      } catch (ExternalTaskException e) {
        consecutiveCancelOrMonitorFailures++;
        LOGGER.warn(String.format("Monitoring of external task failed %s/%s",
            consecutiveCancelOrMonitorFailures, MAX_CANCEL_OR_MONITOR_FAILURES), e);
        if (consecutiveCancelOrMonitorFailures == MAX_CANCEL_OR_MONITOR_FAILURES) {
          break;
        }
      }
    } while (taskState != TaskState.DROPPED && taskState != TaskState.PROCESSED);

    preparePluginStateAndFinishedDate(abstractMetisPlugin, taskState,
        consecutiveCancelOrMonitorFailures);
  }

  private boolean shouldPluginBeCancelled(AbstractMetisPlugin abstractMetisPlugin,
      AtomicInteger previousProcessedRecords,
      AtomicLong checkPointDateOfProcessedRecordsPeriodInMillis) {
    // A plugin with CLEANING state is NOT cancellable, it will be when the state is updated
    return ((abstractMetisPlugin.getPluginStatus() != PluginStatus.CLEANING && workflowExecutionDao
        .isCancelling(workflowExecution.getId())) || isMinuteCapOverWithoutChangeInProcessedRecords(
        abstractMetisPlugin, previousProcessedRecords,
        checkPointDateOfProcessedRecordsPeriodInMillis));
  }

  private boolean isMinuteCapOverWithoutChangeInProcessedRecords(
      AbstractMetisPlugin abstractMetisPlugin, AtomicInteger previousProcessedRecords,
      AtomicLong checkPointDateOfProcessedRecordsPeriodInMillis) {
    final int processedRecords = abstractMetisPlugin.getExecutionProgress().getProcessedRecords();
    //If CLEANING is in progress then just reset the values to be sure and return false
    //Or if we have progress
    if (abstractMetisPlugin.getPluginStatus() == PluginStatus.CLEANING
        || previousProcessedRecords.get() != processedRecords) {
      checkPointDateOfProcessedRecordsPeriodInMillis.set(System.currentTimeMillis());
      previousProcessedRecords.set(processedRecords);
      return false;
    }

    final boolean isMinuteCapOverWithoutChangeInProcessedRecords =
        TimeUnit.MILLISECONDS.toSeconds(
            System.currentTimeMillis() - checkPointDateOfProcessedRecordsPeriodInMillis.get())
            >= periodOfNoProcessedRecordsChangeInSeconds;
    if (isMinuteCapOverWithoutChangeInProcessedRecords) {
      //Request cancelling of the execution
      workflowExecutionDao.setCancellingState(workflowExecution);
    }
    return isMinuteCapOverWithoutChangeInProcessedRecords;
  }

  private void preparePluginStateAndFinishedDate(AbstractMetisPlugin abstractMetisPlugin,
      TaskState taskState, int consecutiveCancelOrMonitorFailures) {
    if (taskState == TaskState.PROCESSED) {
      abstractMetisPlugin.setFinishedDate(new Date());
      abstractMetisPlugin.setPluginStatus(PluginStatus.FINISHED);
    } else if ((taskState == TaskState.DROPPED
        && !workflowExecutionDao.isCancelling(workflowExecution.getId()))
        || consecutiveCancelOrMonitorFailures == MAX_CANCEL_OR_MONITOR_FAILURES) {
      abstractMetisPlugin.setPluginStatus(PluginStatus.FAILED);
    }
    workflowExecutionDao.updateWorkflowPlugins(workflowExecution);
  }

  private void periodicCheckingLoopMocked(long sleepTime, AbstractMetisPlugin abstractMetisPlugin) {
    for (int i = 1; i <= MONITOR_ITERATIONS_TO_FAKE; i++) {
      try {
        if (workflowExecutionDao.isCancelling(workflowExecution.getId())) {
          return;
        }
        Thread.sleep(sleepTime);
        fakeMonitorUpdateProcessedRecords(abstractMetisPlugin, i);
        Date updatedDate = new Date();
        abstractMetisPlugin.setUpdatedDate(updatedDate);
        workflowExecution.setUpdatedDate(updatedDate);
        workflowExecutionDao.updateMonitorInformation(workflowExecution);
      } catch (InterruptedException e) {
        LOGGER.warn("Thread was interrupted", e);
        Thread.currentThread().interrupt();
        return;
      }
    }
    abstractMetisPlugin.setFinishedDate(new Date());
    abstractMetisPlugin.setPluginStatus(PluginStatus.FINISHED);
    workflowExecutionDao.updateWorkflowPlugins(workflowExecution);
  }

  private void fakeMonitorUpdateProcessedRecords(AbstractMetisPlugin abstractMetisPlugin,
      int iteration) {
    ExecutionProgress executionProgress = abstractMetisPlugin.getExecutionProgress();
    executionProgress.setExpectedRecords(MONITOR_ITERATIONS_TO_FAKE * FAKE_RECORDS_PER_ITERATION);
    executionProgress.setProcessedRecords(iteration * FAKE_RECORDS_PER_ITERATION);
    abstractMetisPlugin.setExecutionProgress(executionProgress);
  }
}
