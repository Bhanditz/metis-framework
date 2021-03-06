package eu.europeana.metis.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import eu.europeana.metis.authentication.user.AccountRole;
import eu.europeana.metis.authentication.user.MetisUser;
import eu.europeana.metis.core.dao.DatasetDao;
import eu.europeana.metis.core.dao.DatasetXsltDao;
import eu.europeana.metis.core.dao.WorkflowDao;
import eu.europeana.metis.core.dao.WorkflowExecutionDao;
import eu.europeana.metis.core.dataset.Dataset;
import eu.europeana.metis.core.dataset.DatasetExecutionInformation;
import eu.europeana.metis.core.dataset.DatasetXslt;
import eu.europeana.metis.core.exceptions.NoDatasetFoundException;
import eu.europeana.metis.core.exceptions.NoWorkflowExecutionFoundException;
import eu.europeana.metis.core.exceptions.NoWorkflowFoundException;
import eu.europeana.metis.core.exceptions.PluginExecutionNotAllowed;
import eu.europeana.metis.core.exceptions.WorkflowAlreadyExistsException;
import eu.europeana.metis.core.exceptions.WorkflowExecutionAlreadyExistsException;
import eu.europeana.metis.core.execution.ExecutionRules;
import eu.europeana.metis.core.execution.WorkflowExecutorManager;
import eu.europeana.metis.core.test.utils.TestObjectFactory;
import eu.europeana.metis.core.workflow.OrderField;
import eu.europeana.metis.core.workflow.ValidationProperties;
import eu.europeana.metis.core.workflow.Workflow;
import eu.europeana.metis.core.workflow.WorkflowExecution;
import eu.europeana.metis.core.workflow.WorkflowStatus;
import eu.europeana.metis.core.workflow.plugins.AbstractMetisPlugin;
import eu.europeana.metis.core.workflow.plugins.AbstractMetisPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.EnrichmentPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ExecutionProgress;
import eu.europeana.metis.core.workflow.plugins.HTTPHarvestPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.IndexToPreviewPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.IndexToPublishPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.OaipmhHarvestPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.PluginType;
import eu.europeana.metis.core.workflow.plugins.TransformationPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ValidationExternalPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ValidationInternalPluginMetadata;
import eu.europeana.metis.exception.BadContentException;
import eu.europeana.metis.exception.GenericMetisException;
import eu.europeana.metis.utils.DateUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-10-06
 */
class TestOrchestratorService {

  private static final int SOLR_COMMIT_PERIOD_IN_MINS = 15;
  private static WorkflowExecutionDao workflowExecutionDao;
  private static WorkflowDao workflowDao;
  private static DatasetDao datasetDao;
  private static DatasetXsltDao datasetXsltDao;
  private static WorkflowExecutorManager workflowExecutorManager;
  private static OrchestratorHelper orchestratorHelper;
  private static OrchestratorService orchestratorService;
  private static RedissonClient redissonClient;
  private static Authorizer authorizer;

  @BeforeAll
  static void prepare() {
    workflowExecutionDao = mock(WorkflowExecutionDao.class);
    workflowDao = mock(WorkflowDao.class);
    datasetDao = mock(DatasetDao.class);
    datasetXsltDao = mock(DatasetXsltDao.class);
    workflowExecutorManager = mock(WorkflowExecutorManager.class);
    redissonClient = mock(RedissonClient.class);
    authorizer = mock(Authorizer.class);

    orchestratorHelper = new OrchestratorHelper(workflowExecutionDao,
        datasetXsltDao);
    orchestratorHelper.setMetisCoreUrl("https://some.url.com");
    orchestratorHelper.setValidationExternalProperties(
        new ValidationProperties("url-ext", "schema-ext", "schematron-ext"));
    orchestratorHelper.setValidationInternalProperties(
        new ValidationProperties("url-int", "schema-int", "schematron-int"));

    orchestratorService = new OrchestratorService(orchestratorHelper, workflowDao,
        workflowExecutionDao, datasetDao, workflowExecutorManager, redissonClient, authorizer);
    orchestratorService.setSolrCommitPeriodInMins(SOLR_COMMIT_PERIOD_IN_MINS);
  }

  @AfterEach
  void cleanUp() {
    Mockito.reset(workflowExecutionDao);
    Mockito.reset(workflowDao);
    Mockito.reset(datasetDao);
    Mockito.reset(workflowExecutorManager);
    Mockito.reset(redissonClient);
    Mockito.reset(authorizer);
  }

  @Test
  void createWorkflow() throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    Dataset dataset = TestObjectFactory.createDataset("datasetName");
    workflow.setDatasetId(dataset.getDatasetId());
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(dataset);
    orchestratorService.createWorkflow(metisUser, workflow.getDatasetId(), workflow);

    verify(authorizer, times(1))
        .authorizeWriteExistingDatasetById(metisUser, workflow.getDatasetId());
    verifyNoMoreInteractions(authorizer);
    InOrder inOrder = Mockito.inOrder(workflowDao);
    inOrder.verify(workflowDao, times(1)).exists(workflow);
    inOrder.verify(workflowDao, times(1)).create(workflow);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void createWorkflowOrderOfPluginsNotAllowed() throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();

    List<AbstractMetisPluginMetadata> metisPluginsMetadata = workflow.getMetisPluginsMetadata();
    List<AbstractMetisPluginMetadata> wrongOrderMetisPluginsMetadata = new ArrayList<>(
        metisPluginsMetadata);
    Collections.copy(wrongOrderMetisPluginsMetadata, metisPluginsMetadata);
    wrongOrderMetisPluginsMetadata.remove(2);
    workflow.setMetisPluginsMetadata(wrongOrderMetisPluginsMetadata);
    Dataset dataset = TestObjectFactory.createDataset("datasetName");
    workflow.setDatasetId(dataset.getDatasetId());
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(dataset);
    assertThrows(PluginExecutionNotAllowed.class,
        () -> orchestratorService.createWorkflow(metisUser, workflow.getDatasetId(), workflow));

    verify(authorizer, times(1))
        .authorizeWriteExistingDatasetById(metisUser, workflow.getDatasetId());
    verifyNoMoreInteractions(authorizer);
    verify(workflowDao, times(1)).exists(workflow);
    verifyNoMoreInteractions(workflowDao);
  }

  @Test
  void createWorkflow_AlreadyExists() {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    Dataset dataset = TestObjectFactory.createDataset("datasetName");
    workflow.setDatasetId(dataset.getDatasetId());
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(dataset);
    when(workflowDao.exists(workflow)).thenReturn(new ObjectId().toString());

    assertThrows(WorkflowAlreadyExistsException.class,
        () -> orchestratorService.createWorkflow(metisUser, workflow.getDatasetId(), workflow));

    InOrder inOrder = Mockito.inOrder(workflowDao);
    inOrder.verify(workflowDao, times(1)).exists(workflow);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void createWorkflow_NoDatasetFoundException() {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    Dataset dataset = TestObjectFactory.createDataset("datasetName");
    workflow.setDatasetId(dataset.getDatasetId());
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(null);
    assertThrows(NoDatasetFoundException.class,
        () -> orchestratorService.createWorkflow(metisUser, workflow.getDatasetId(), workflow));
  }

  @Test
  void updateWorkflow() throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    Dataset dataset = TestObjectFactory.createDataset("datasetName");
    workflow.setDatasetId(dataset.getDatasetId());
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(dataset);
    when(workflowDao.getWorkflow(dataset.getDatasetId())).thenReturn(workflow);
    orchestratorService.updateWorkflow(metisUser, workflow.getDatasetId(), workflow);
    verify(authorizer, times(1))
        .authorizeWriteExistingDatasetById(metisUser, workflow.getDatasetId());
    verifyNoMoreInteractions(authorizer);
    InOrder inOrder = Mockito.inOrder(workflowDao);
    inOrder.verify(workflowDao, times(1)).getWorkflow(dataset.getDatasetId());
    inOrder.verify(workflowDao, times(1)).update(workflow);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void updateWorkflow_NoDatasetFoundException() {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    Dataset dataset = TestObjectFactory.createDataset("datasetName");
    workflow.setDatasetId(dataset.getDatasetId());
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(null);
    assertThrows(NoDatasetFoundException.class,
        () -> orchestratorService.updateWorkflow(metisUser, workflow.getDatasetId(), workflow));
  }

  @Test
  void updateUserWorkflow_NoUserWorkflowFound() {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    Dataset dataset = TestObjectFactory.createDataset("datasetName");
    workflow.setDatasetId(dataset.getDatasetId());
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(dataset);
    assertThrows(NoWorkflowFoundException.class,
        () -> orchestratorService.updateWorkflow(metisUser, workflow.getDatasetId(), workflow));
    InOrder inOrder = Mockito.inOrder(workflowDao);
    inOrder.verify(workflowDao, times(1)).getWorkflow(anyString());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void deleteWorkflow() throws GenericMetisException {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    orchestratorService.deleteWorkflow(metisUser, workflow.getDatasetId());
    verify(authorizer, times(1))
        .authorizeWriteExistingDatasetById(metisUser, workflow.getDatasetId());
    verifyNoMoreInteractions(authorizer);
    ArgumentCaptor<String> workflowDatasetIdArgumentCaptor = ArgumentCaptor
        .forClass(String.class);
    verify(workflowDao, times(1)).deleteWorkflow(workflowDatasetIdArgumentCaptor.capture());
    assertEquals(workflow.getDatasetId(),
        workflowDatasetIdArgumentCaptor.getValue());
  }

  @Test
  void getWorkflow() throws GenericMetisException {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);

    Workflow retrievedWorkflow = orchestratorService
        .getWorkflow(metisUser, workflow.getDatasetId());
    verify(authorizer, times(1))
        .authorizeReadExistingDatasetById(metisUser, workflow.getDatasetId());
    verifyNoMoreInteractions(authorizer);
    assertSame(workflow, retrievedWorkflow);
  }

  @Test
  void getWorkflowExecutionByExecutionId() throws GenericMetisException {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    when(workflowExecutionDao.getById(anyString())).thenReturn(workflowExecution);
    orchestratorService.getWorkflowExecutionByExecutionId(metisUser, anyString());
    verify(authorizer, times(1)).authorizeReadExistingDatasetById(eq(metisUser), anyString());
    verifyNoMoreInteractions(authorizer);
    InOrder inOrder = Mockito.inOrder(workflowExecutionDao);
    inOrder.verify(workflowExecutionDao, times(1)).getById(anyString());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void getWorkflowExecutionByExecutionId_NonExistingWorkflowExecution()
      throws GenericMetisException {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    when(workflowExecutionDao.getById(anyString())).thenReturn(null);
    orchestratorService.getWorkflowExecutionByExecutionId(metisUser, anyString());
    verifyNoMoreInteractions(authorizer);
    InOrder inOrder = Mockito.inOrder(workflowExecutionDao);
    inOrder.verify(workflowExecutionDao, times(1)).getById(anyString());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions() throws Exception {

    // Create the test objects
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(datasetDao.getDatasetByDatasetId(dataset.getDatasetId())).thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    RLock rlock = mock(RLock.class);
    when(redissonClient.getFairLock(anyString())).thenReturn(rlock);
    doNothing().when(rlock).lock();
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId())).thenReturn(null);
    String objectId = new ObjectId().toString();
    DatasetXslt datasetXslt = TestObjectFactory.createXslt(dataset);
    datasetXslt.setId(new ObjectId(TestObjectFactory.XSLTID));
    when(datasetXsltDao.getLatestXsltForDatasetId("-1")).thenReturn(datasetXslt);
    when(workflowExecutionDao.create(any(WorkflowExecution.class))).thenReturn(objectId);
    doNothing().when(rlock).unlock();
    doNothing().when(workflowExecutorManager).addWorkflowExecutionToQueue(objectId, 0);

    // Add the workflow
    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0);
    verify(authorizer, times(1))
        .authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId());
    verifyNoMoreInteractions(authorizer);

    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutionsWithoutAuthorization(dataset.getDatasetId(), null,
            0);
    verifyNoMoreInteractions(authorizer);

    // Verify the validation parameters
    final ValidationInternalPluginMetadata metadataInternal =
        (ValidationInternalPluginMetadata) workflow
            .getPluginMetadata(PluginType.VALIDATION_INTERNAL);
    assertEquals(orchestratorHelper.getValidationInternalProperties().getUrlOfSchemasZip(),
        metadataInternal.getUrlOfSchemasZip());
    assertEquals(orchestratorHelper.getValidationInternalProperties().getSchemaRootPath(),
        metadataInternal.getSchemaRootPath());
    assertEquals(orchestratorHelper.getValidationInternalProperties().getSchematronRootPath(),
        metadataInternal.getSchematronRootPath());
    final ValidationExternalPluginMetadata metadataExternal =
        (ValidationExternalPluginMetadata) workflow
            .getPluginMetadata(PluginType.VALIDATION_EXTERNAL);
    assertEquals(orchestratorHelper.getValidationExternalProperties().getUrlOfSchemasZip(),
        metadataExternal.getUrlOfSchemasZip());
    assertEquals(orchestratorHelper.getValidationExternalProperties().getSchemaRootPath(),
        metadataExternal.getSchemaRootPath());
    assertEquals(orchestratorHelper.getValidationExternalProperties().getSchematronRootPath(),
        metadataExternal.getSchematronRootPath());
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_TransformationUsesCustomXslt()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    workflow.getMetisPluginsMetadata().forEach(abstractMetisPluginMetadata -> {
      if (abstractMetisPluginMetadata instanceof TransformationPluginMetadata) {
        ((TransformationPluginMetadata) abstractMetisPluginMetadata).setCustomXslt(true);
      }
    });
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    RLock rlock = mock(RLock.class);
    when(redissonClient.getFairLock(anyString())).thenReturn(rlock);
    doNothing().when(rlock).lock();
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId())).thenReturn(null);
    String objectId = new ObjectId().toString();
    DatasetXslt datasetXslt = TestObjectFactory.createXslt(dataset);
    datasetXslt.setId(new ObjectId(TestObjectFactory.XSLTID));
    dataset.setXsltId(datasetXslt.getId());
    when(datasetXsltDao.getById(dataset.getXsltId().toString())).thenReturn(datasetXslt);
    when(workflowExecutionDao.create(any(WorkflowExecution.class))).thenReturn(objectId);
    doNothing().when(rlock).unlock();
    doNothing().when(workflowExecutorManager).addWorkflowExecutionToQueue(objectId, 0);
    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0);
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_AddHTTPHarvest()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    HTTPHarvestPluginMetadata httpHarvestPluginMetadata = new HTTPHarvestPluginMetadata();
    httpHarvestPluginMetadata.setEnabled(true);
    workflow.getMetisPluginsMetadata().set(0, httpHarvestPluginMetadata);
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    when(redissonClient.getFairLock(anyString())).thenReturn(Mockito.mock(RLock.class));
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId())).thenReturn(null);
    String objectId = new ObjectId().toString();
    when(workflowExecutionDao.create(any(WorkflowExecution.class))).thenReturn(objectId);
    doNothing().when(workflowExecutorManager).addWorkflowExecutionToQueue(objectId, 0);
    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0);
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_NoHarvestPlugin()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    workflow.getMetisPluginsMetadata().remove(0);

    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    AbstractMetisPlugin oaipmhHarvestPlugin = PluginType.OAIPMH_HARVEST.getNewPlugin(null);
    oaipmhHarvestPlugin.setStartedDate(new Date());
    ExecutionProgress executionProgress = new ExecutionProgress();
    executionProgress.setProcessedRecords(5);
    oaipmhHarvestPlugin.setExecutionProgress(executionProgress);
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(dataset.getDatasetId(),
            ExecutionRules.getHarvestPluginGroup())).thenReturn(oaipmhHarvestPlugin);
    RLock rlock = mock(RLock.class);
    when(redissonClient.getFairLock(anyString())).thenReturn(rlock);
    doNothing().when(rlock).lock();
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId())).thenReturn(null);
    String objectId = new ObjectId().toString();
    when(workflowExecutionDao.create(any(WorkflowExecution.class))).thenReturn(objectId);
    doNothing().when(rlock).unlock();
    doNothing().when(workflowExecutorManager).addWorkflowExecutionToQueue(objectId, 0);
    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0);
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_NoHarvestPlugin_NoProcessPlugin()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    List<AbstractMetisPluginMetadata> abstractMetisPluginMetadata = new ArrayList<>();
    EnrichmentPluginMetadata enrichmentPluginMetadata = new EnrichmentPluginMetadata();
    enrichmentPluginMetadata.setEnabled(true);
    abstractMetisPluginMetadata.add(enrichmentPluginMetadata);
    workflow.setMetisPluginsMetadata(abstractMetisPluginMetadata);

    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    when(redissonClient.getFairLock(anyString())).thenReturn(Mockito.mock(RLock.class));
    AbstractMetisPlugin oaipmhHarvestPlugin = PluginType.OAIPMH_HARVEST.getNewPlugin(null);
    oaipmhHarvestPlugin.setStartedDate(new Date());
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(dataset.getDatasetId(),
            ExecutionRules.getHarvestPluginGroup())).thenReturn(oaipmhHarvestPlugin);
    assertThrows(PluginExecutionNotAllowed.class, () -> orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0));
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutionsEcloudDatasetAlreadyGenerated()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    dataset.setEcloudDatasetId("f525f64c-fea0-44bf-8c56-88f30962734c");
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    when(redissonClient.getFairLock(anyString())).thenReturn(Mockito.mock(RLock.class));
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId())).thenReturn(null);
    String objectId = new ObjectId().toString();
    when(workflowExecutionDao.create(any(WorkflowExecution.class))).thenReturn(objectId);
    doNothing().when(workflowExecutorManager).addWorkflowExecutionToQueue(objectId, 0);
    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0);
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutionsEcloudDatasetAlreadyExistsInEcloud()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    when(datasetDao.checkAndCreateDatasetInEcloud(any(Dataset.class)))
        .thenReturn(UUID.randomUUID().toString());
    RLock rlock = mock(RLock.class);
    when(redissonClient.getFairLock(anyString())).thenReturn(rlock);
    doNothing().when(rlock).lock();
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId())).thenReturn(null);
    String objectId = new ObjectId().toString();
    when(workflowExecutionDao.create(any(WorkflowExecution.class))).thenReturn(objectId);
    doNothing().when(rlock).unlock();
    doNothing().when(workflowExecutorManager).addWorkflowExecutionToQueue(objectId, 0);
    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0);
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutionsEcloudDatasetCreationFails()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(workflow.getDatasetId())).thenReturn(workflow);
    when(datasetDao.checkAndCreateDatasetInEcloud(any(Dataset.class)))
        .thenReturn(UUID.randomUUID().toString());
    when(redissonClient.getFairLock(anyString())).thenReturn(Mockito.mock(RLock.class));
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId())).thenReturn(null);
    String objectId = new ObjectId().toString();
    when(workflowExecutionDao.create(any(WorkflowExecution.class))).thenReturn(objectId);
    doNothing().when(workflowExecutorManager).addWorkflowExecutionToQueue(objectId, 0);
    orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0);
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_NoDatasetFoundException()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, datasetId))
        .thenThrow(NoDatasetFoundException.class);
    assertThrows(NoDatasetFoundException.class, () -> orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, datasetId, null, 0));
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_NoDatasetFoundException_Unauthorized() {
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    when(datasetDao.getDatasetByDatasetId(datasetId)).thenReturn(null);
    assertThrows(NoDatasetFoundException.class, () -> orchestratorService
        .addWorkflowInQueueOfWorkflowExecutionsWithoutAuthorization(datasetId, null, 0));
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_NoWorkflowFoundException()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(dataset.getDatasetId())).thenReturn(null);
    assertThrows(NoWorkflowFoundException.class, () -> orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0));
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_WorkflowIsEmpty()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = new Workflow();
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(dataset.getDatasetId())).thenReturn(workflow);
    assertThrows(BadContentException.class, () -> orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0));
  }

  @Test
  void addWorkflowInQueueOfWorkflowExecutions_WorkflowExecutionAlreadyExistsException()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    Dataset dataset = TestObjectFactory.createDataset(TestObjectFactory.DATASETNAME);
    Workflow workflow = TestObjectFactory.createWorkflowObject();
    when(authorizer.authorizeWriteExistingDatasetById(metisUser, dataset.getDatasetId()))
        .thenReturn(dataset);
    when(workflowDao.getWorkflow(dataset.getDatasetId())).thenReturn(workflow);
    when(redissonClient.getFairLock(anyString())).thenReturn(Mockito.mock(RLock.class));
    when(workflowExecutionDao.existsAndNotCompleted(dataset.getDatasetId()))
        .thenReturn(new ObjectId().toString());
    assertThrows(WorkflowExecutionAlreadyExistsException.class, () -> orchestratorService
        .addWorkflowInQueueOfWorkflowExecutions(metisUser, dataset.getDatasetId(), null, 0));
  }

  @Test
  void cancelWorkflowExecution() throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    when(workflowExecutionDao.getById(TestObjectFactory.EXECUTIONID)).thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).setCancellingState(workflowExecution);
    orchestratorService.cancelWorkflowExecution(metisUser, TestObjectFactory.EXECUTIONID);
    verify(authorizer, times(1))
        .authorizeWriteExistingDatasetById(metisUser, workflowExecution.getDatasetId());
    verifyNoMoreInteractions(authorizer);
  }

  @Test
  void cancelWorkflowExecution_NoWorkflowExecutionFoundException() {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    when(workflowExecutionDao.getById(TestObjectFactory.EXECUTIONID)).thenReturn(null);
    assertThrows(NoWorkflowExecutionFoundException.class, () -> orchestratorService
        .cancelWorkflowExecution(metisUser, TestObjectFactory.EXECUTIONID));
    verifyNoMoreInteractions(workflowExecutorManager);
  }

  @Test
  void getWorkflowExecutionsPerRequest() {
    orchestratorService.getWorkflowExecutionsPerRequest();
    verify(workflowExecutionDao, times(1)).getWorkflowExecutionsPerRequest();
  }

  @Test
  void getWorkflowsPerRequest() {
    orchestratorService.getWorkflowsPerRequest();
    verify(workflowDao, times(1)).getWorkflowsPerRequest();
  }


  @Test
  void getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution_HarvestPlugin()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    assertNull(orchestratorService
        .getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution(metisUser,
            datasetId, PluginType.OAIPMH_HARVEST, null));
    verify(authorizer, times(1)).authorizeReadExistingDatasetById(metisUser, datasetId);
    verifyNoMoreInteractions(authorizer);
  }

  @Test
  void getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution_ProcessPlugin()
      throws Exception {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    AbstractMetisPlugin oaipmhHarvestPlugin = PluginType.OAIPMH_HARVEST.getNewPlugin(null);
    ExecutionProgress executionProgress = new ExecutionProgress();
    executionProgress.setProcessedRecords(5);
    oaipmhHarvestPlugin.setExecutionProgress(executionProgress);
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(datasetId,
            ExecutionRules.getHarvestPluginGroup())).thenReturn(oaipmhHarvestPlugin);
    assertEquals(PluginType.OAIPMH_HARVEST, orchestratorService
        .getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution(metisUser,
            datasetId, PluginType.VALIDATION_EXTERNAL, null).getPluginType());
    verify(authorizer, times(1)).authorizeReadExistingDatasetById(metisUser, datasetId);
    verifyNoMoreInteractions(authorizer);
  }

  @Test
  void getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution_PluginExecutionNotAllowed() {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(datasetId,
            ExecutionRules.getHarvestPluginGroup())).thenReturn(null);
    assertThrows(PluginExecutionNotAllowed.class, () -> orchestratorService
        .getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution(metisUser,
            datasetId, PluginType.VALIDATION_EXTERNAL, null));
  }

  @Test
  void getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution_PluginExecutionNotAllowed_ProcessedRecordSameAsErrors() {
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(datasetId,
            ExecutionRules.getHarvestPluginGroup()))
        .thenReturn(PluginType.OAIPMH_HARVEST.getNewPlugin(null));
    assertThrows(PluginExecutionNotAllowed.class, () -> orchestratorService
        .getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution(metisUser,
            datasetId, PluginType.VALIDATION_EXTERNAL, null));
  }

  @Test
  void getAllWorkflowExecutionsByDatasetId() throws GenericMetisException {

    // Define some constants
    final int nextPage = 1;
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    final Set<WorkflowStatus> workflowStatuses = Collections.singleton(WorkflowStatus.INQUEUE);

    // Check with specific dataset ID: should query only that dataset.
    orchestratorService.getAllWorkflowExecutions(metisUser, datasetId, workflowStatuses,
        OrderField.ID, false, nextPage);
    verify(authorizer, times(1)).authorizeReadExistingDatasetById(metisUser, datasetId);
    verifyNoMoreInteractions(authorizer);
    verify(workflowExecutionDao, times(1)).getAllWorkflowExecutions(
        eq(Collections.singleton(datasetId)), eq(workflowStatuses), eq(OrderField.ID), eq(false),
        eq(nextPage));
    verifyNoMoreInteractions(workflowExecutionDao);
  }

  @Test
  void getAllWorkflowExecutionsForRegularUser() throws GenericMetisException {

    // Define some constants
    final int nextPage = 1;
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final Set<String> datasetIds = new HashSet<>(Arrays.asList("A", "B", "C"));
    final List<Dataset> datasets = datasetIds.stream().map(id -> {
      final Dataset result = new Dataset();
      result.setDatasetId(id);
      return result;
    }).collect(Collectors.toList());
    final Set<WorkflowStatus> workflowStatuses = Collections.singleton(WorkflowStatus.INQUEUE);

    // Check for all datasets and for regular user: should query all datasets to which that user's
    // organization has rights.
    when(datasetDao.getAllDatasetsByOrganizationId(metisUser.getOrganizationId()))
        .thenReturn(datasets);
    orchestratorService.getAllWorkflowExecutions(metisUser, null, workflowStatuses,
        OrderField.CREATED_DATE, false, nextPage);
    verify(authorizer, times(1)).authorizeReadAllDatasets(metisUser);
    verifyNoMoreInteractions(authorizer);
    verify(workflowExecutionDao, times(1)).getAllWorkflowExecutions(eq(datasetIds),
        eq(workflowStatuses), eq(OrderField.CREATED_DATE), eq(false), eq(nextPage));
    verifyNoMoreInteractions(workflowExecutionDao);
  }

  @Test
  void getAllWorkflowExecutionsForAdmin() throws GenericMetisException {

    // Define some constants
    final int nextPage = 1;
    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final Set<WorkflowStatus> workflowStatuses = Collections.singleton(WorkflowStatus.INQUEUE);

    // Check for all datasets and for admin user: should query all datasets.
    metisUser.setAccountRole(AccountRole.METIS_ADMIN);
    orchestratorService.getAllWorkflowExecutions(metisUser, null, workflowStatuses,
        OrderField.CREATED_DATE, true, nextPage);
    verify(authorizer, times(1)).authorizeReadAllDatasets(metisUser);
    verifyNoMoreInteractions(authorizer);
    verify(workflowExecutionDao, times(1)).getAllWorkflowExecutions(isNull(), eq(workflowStatuses),
        eq(OrderField.CREATED_DATE), eq(true), eq(nextPage));
    verifyNoMoreInteractions(workflowExecutionDao);
  }

  @Test
  void getDatasetExecutionInformation() throws GenericMetisException {
    ExecutionProgress executionProgress = new ExecutionProgress();
    executionProgress.setProcessedRecords(100);
    executionProgress.setErrors(20);
    AbstractMetisPlugin oaipmhHarvestPlugin =
        PluginType.OAIPMH_HARVEST.getNewPlugin(new OaipmhHarvestPluginMetadata());
    oaipmhHarvestPlugin.setFinishedDate(
        DateUtils.modifyDateByTimeUnitAmount(new Date(), -(SOLR_COMMIT_PERIOD_IN_MINS + 3),
            TimeUnit.MINUTES));
    oaipmhHarvestPlugin.setExecutionProgress(executionProgress);
    AbstractMetisPlugin firstPublishPlugin =
        PluginType.PUBLISH.getNewPlugin(new IndexToPublishPluginMetadata());
    firstPublishPlugin.setFinishedDate(
        DateUtils.modifyDateByTimeUnitAmount(new Date(), -(SOLR_COMMIT_PERIOD_IN_MINS + 2),
            TimeUnit.MINUTES));
    firstPublishPlugin.setExecutionProgress(executionProgress);
    AbstractMetisPlugin lastPreviewPlugin =
        PluginType.PREVIEW.getNewPlugin(new IndexToPreviewPluginMetadata());
    lastPreviewPlugin.setFinishedDate(
        DateUtils.modifyDateByTimeUnitAmount(new Date(), -(SOLR_COMMIT_PERIOD_IN_MINS + 1),
            TimeUnit.MINUTES));
    lastPreviewPlugin.setExecutionProgress(executionProgress);
    AbstractMetisPlugin lastPublishPlugin =
        PluginType.PUBLISH.getNewPlugin(new IndexToPublishPluginMetadata());
    lastPublishPlugin
        .setFinishedDate(DateUtils
            .modifyDateByTimeUnitAmount(new Date(), -SOLR_COMMIT_PERIOD_IN_MINS, TimeUnit.MINUTES));
    lastPublishPlugin.setExecutionProgress(executionProgress);

    final MetisUser metisUser = TestObjectFactory.createMetisUser(TestObjectFactory.EMAIL);
    final String datasetId = Integer.toString(TestObjectFactory.DATASETID);
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(datasetId,
            EnumSet.of(PluginType.HTTP_HARVEST, PluginType.OAIPMH_HARVEST)))
        .thenReturn(oaipmhHarvestPlugin);
    when(workflowExecutionDao
        .getFirstFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(
            datasetId, EnumSet.of(PluginType.PUBLISH))).thenReturn(firstPublishPlugin);
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(datasetId,
            EnumSet.of(PluginType.PUBLISH))).thenReturn(lastPublishPlugin);
    when(workflowExecutionDao
        .getLastFinishedWorkflowExecutionPluginByDatasetIdAndPluginType(datasetId,
            EnumSet.of(PluginType.PREVIEW))).thenReturn(lastPreviewPlugin);

    DatasetExecutionInformation datasetExecutionInformation = orchestratorService
        .getDatasetExecutionInformation(metisUser, datasetId);

    verify(authorizer, times(1)).authorizeReadExistingDatasetById(metisUser, datasetId);
    verifyNoMoreInteractions(authorizer);
    assertEquals(oaipmhHarvestPlugin.getFinishedDate(),
        datasetExecutionInformation.getLastHarvestedDate());
    assertEquals(
        oaipmhHarvestPlugin.getExecutionProgress().getProcessedRecords() - oaipmhHarvestPlugin
            .getExecutionProgress().getErrors(),
        datasetExecutionInformation.getLastHarvestedRecords());
    assertEquals(lastPreviewPlugin.getFinishedDate(),
        datasetExecutionInformation.getLastPreviewDate());
    assertEquals(firstPublishPlugin.getFinishedDate(),
        datasetExecutionInformation.getFirstPublishedDate());
    assertEquals(lastPublishPlugin.getFinishedDate(),
        datasetExecutionInformation.getLastPublishedDate());
    assertEquals(
        lastPublishPlugin.getExecutionProgress().getProcessedRecords() - lastPublishPlugin
            .getExecutionProgress().getErrors(),
        datasetExecutionInformation.getLastPublishedRecords());
    assertTrue(datasetExecutionInformation.isLastPreviewRecordsReadyForViewing());
    assertFalse(datasetExecutionInformation.isLastPublishedRecordsReadyForViewing());
  }
}
