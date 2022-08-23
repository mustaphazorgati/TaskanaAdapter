package pro.taskana.adapter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.camunda.bpm.engine.impl.calendar.DateTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ContextConfiguration;
import uk.co.datumedge.hamcrest.json.SameJSONAs;

import pro.taskana.adapter.manager.AdapterManager;
import pro.taskana.adapter.systemconnector.api.SystemConnector;
import pro.taskana.adapter.systemconnector.camunda.api.impl.CamundaSystemConnectorImpl;
import pro.taskana.adapter.systemconnector.camunda.config.CamundaSystemUrls.SystemUrlInfo;
import pro.taskana.adapter.test.TaskanaAdapterTestApplication;
import pro.taskana.common.api.exceptions.NotAuthorizedException;
import pro.taskana.common.internal.util.Pair;
import pro.taskana.common.test.security.JaasExtension;
import pro.taskana.common.test.security.WithAccessId;
import pro.taskana.task.api.TaskCustomIntField;
import pro.taskana.task.api.exceptions.TaskNotFoundException;
import pro.taskana.task.api.models.Task;
import pro.taskana.task.api.models.TaskSummary;

/** Test class to test the conversion of tasks generated by Camunda BPM to Taskana tasks. */
@SpringBootTest(
    classes = TaskanaAdapterTestApplication.class,
    webEnvironment = WebEnvironment.DEFINED_PORT)
@AutoConfigureWebTestClient
@ExtendWith(JaasExtension.class)
@ContextConfiguration
@SuppressWarnings("checkstyle:LineLength")
class TestTaskAcquisition extends AbsIntegrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskAcquisition.class);
  @Autowired AdapterManager adapterManager;

  @Value("${taskana-system-connector-camundaSystemURLs}")
  private String configuredSystemConnectorUrls;

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void
      should_CreateTaskanaTasksWithVariablesInCustomAttributes_When_StartCamundaTaskWithTheseComplexVariables()
          throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_with_complex_variables_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    String expectedComplexProcessVariable =
        "{\"type\":\"object\","
            + "\"value\":\""
            + "{\\\"stringField\\\":\\\"\\\\fForm feed \\\\b Backspace \\\\t Tab"
            + " \\\\\\\\Backslash \\\\n newLine \\\\r Carriage return \\\\\\\" DoubleQuote\\\","
            + "\\\"intField\\\":1,\\\"doubleField\\\":1.1,\\\"booleanField\\\":false,"
            + "\\\"processVariableTestObjectTwoField\\\":["
            + "{\\\"stringFieldObjectTwo\\\":\\\"stringValueObjectTwo\\\","
            + "\\\"intFieldObjectTwo\\\":2,\\\"doubleFieldObjectTwo\\\":2.2,"
            + "\\\"booleanFieldObjectTwo\\\":true,"
            + "\\\"dateFieldObjectTwo\\\":\\\"1970-01-01 13:12:11\\\"}]}\","
            + "\"valueInfo\":{\"objectTypeName\":\"pro.taskana.impl.ProcessVariableTestObject\","
            + "\"serializationDataFormat\":\"application/json\"}}";

    String expectedPrimitiveProcessVariable1 =
        "{\"type\":\"integer\",\"value\":5," + "\"valueInfo\":null}";

    String expectedPrimitiveProcessVariable2 =
        "{\"type\":\"boolean\",\"value\":true," + "\"valueInfo\":null}";

    camundaTaskIds.forEach(
        camundaTaskId -> {
          Map<String, String> customAttributes =
              retrieveCustomAttributesFromNewTaskanaTask(camundaTaskId);

          assertThat(
              expectedComplexProcessVariable,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute1")));
          assertThat(
              expectedPrimitiveProcessVariable1,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute2")));
          assertThat(
              expectedPrimitiveProcessVariable2,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute3")));
        });
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_CreateTaskanaTask_When_StartUserTaskProcessInstanceInCamunda() throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    for (String camundaTaskId : camundaTaskIds) {
      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertThat(taskanaTasks).hasSize(1);
      TaskSummary taskanaTaskSummary = taskanaTasks.get(0);
      String taskanaTaskExternalId = taskanaTaskSummary.getExternalId();
      assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);
      String businessProcessId = taskanaTaskSummary.getBusinessProcessId();
      assertThat(processInstanceId).isEqualTo(businessProcessId);
    }
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void
      should_CreateTaskanaTask_When_StartUserTaskProcessInstanceWithEmptyExtensionPropertyInCamunda()
          throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process_with_empty_extension_property", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    for (String camundaTaskId : camundaTaskIds) {
      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertThat(taskanaTasks).hasSize(1);
      TaskSummary taskanaTaskSummary = taskanaTasks.get(0);
      String taskanaTaskExternalId = taskanaTaskSummary.getExternalId();
      assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);
      String businessProcessId = taskanaTaskSummary.getBusinessProcessId();
      assertThat(processInstanceId).isEqualTo(businessProcessId);
    }

    String expectedComplexProcessVariable =
        "{\"type\":\"object\","
            + "\"value\":\""
            + "{\\\"stringField\\\":\\\"\\\\fForm feed \\\\b Backspace \\\\t Tab"
            + " \\\\\\\\Backslash \\\\n newLine \\\\r Carriage return \\\\\\\" DoubleQuote\\\","
            + "\\\"intField\\\":1,\\\"doubleField\\\":1.1,\\\"booleanField\\\":false,"
            + "\\\"processVariableTestObjectTwoField\\\":["
            + "{\\\"stringFieldObjectTwo\\\":\\\"stringValueObjectTwo\\\","
            + "\\\"intFieldObjectTwo\\\":2,\\\"doubleFieldObjectTwo\\\":2.2,"
            + "\\\"booleanFieldObjectTwo\\\":true,"
            + "\\\"dateFieldObjectTwo\\\":\\\"1970-01-01 13:12:11\\\"}]}\","
            + "\"valueInfo\":{\"objectTypeName\":\"pro.taskana.impl.ProcessVariableTestObject\","
            + "\"serializationDataFormat\":\"application/json\"}}";

    String expectedPrimitiveProcessVariable1 =
        "{\"type\":\"integer\",\"value\":5," + "\"valueInfo\":null}";

    String expectedPrimitiveProcessVariable2 =
        "{\"type\":\"boolean\",\"value\":true," + "\"valueInfo\":null}";

    camundaTaskIds.forEach(
        camundaTaskId -> {
          Map<String, String> customAttributes =
              retrieveCustomAttributesFromNewTaskanaTask(camundaTaskId);

          assertThat(
              expectedComplexProcessVariable,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute1")));
          assertThat(
              expectedPrimitiveProcessVariable1,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute2")));
          assertThat(
              expectedPrimitiveProcessVariable2,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute3")));
        });
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_CreateMultipleTaskanaTasks_When_StartMultipleUserTaskProcessInstanceInCamunda()
      throws Exception {

    int numberOfProcesses = 10;
    List<List<String>> camundaTaskIdsList = new ArrayList<>();
    for (int i = 0; i < numberOfProcesses; i++) {
      String processInstanceId =
          this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
              "simple_user_task_process", "");
      camundaTaskIdsList.add(
          this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId));
    }
    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    for (List<String> camundaTaskIds : camundaTaskIdsList) {
      for (String camundaTaskId : camundaTaskIds) {
        List<TaskSummary> taskanaTasks =
            this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
        assertThat(taskanaTasks).hasSize(1);
        String taskanaTaskExternalId = taskanaTasks.get(0).getExternalId();
        assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);
      }
    }
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_CreateTaskanaTask_When_StartCamundaTaskWithPrimitiveVariables() throws Exception {

    String variables =
        "\"variables\": {\"amount\": {\"value\":555, "
            + "\"type\":\"long\"},\"item\": {\"value\": \"item-xyz\"}}";
    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", variables);
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    String expectedPrimitiveVariable1 = "{\"type\":\"long\",\"value\":555,\"valueInfo\":null}";

    String expectedPrimitiveVariable2 =
        "{\"type\":\"string\",\"value\":\"item-xyz\",\"valueInfo\":null}";

    camundaTaskIds.forEach(
        camundaTaskId -> {
          Map<String, String> customAttributes =
              retrieveCustomAttributesFromNewTaskanaTask(camundaTaskId);
          assertThat(
              expectedPrimitiveVariable1,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:amount")));
          assertThat(
              expectedPrimitiveVariable2,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:item")));
        });
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_CreateTaskanaTaskWithManualPriority_When_StartCamundaTaskWithThisManualPriority()
      throws Exception {

    String variables =
        "\"variables\": {\"taskana.manual-priority\": {\"value\":\"555\", "
            + "\"type\":\"string\"}}";
    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", variables);
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    TaskSummary taskanaTask =
        taskService.createTaskQuery().externalIdIn(camundaTaskIds.get(0)).single();

    assertThat(taskanaTask.getManualPriority()).isEqualTo(555);
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void
      should_CreateTaskanaTaskWithDefaultManualPriority_When_StartCamundaTaskWithoutManualPriority()
          throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    TaskSummary taskanaTask =
        taskService.createTaskQuery().externalIdIn(camundaTaskIds.get(0)).single();

    assertThat(taskanaTask.getManualPriority()).isEqualTo(-1);
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_SetCustomIntegersInTaskanaTask_When_CamundaTaskHasCustomIntegers() throws Exception {
    String variables =
        "\"variables\": {"
            + "\"taskana.custom-int-1\": {\"value\":\"1\", \"type\":\"string\"},"
            + "\"taskana.custom-int-2\": {\"value\":\"2\", \"type\":\"string\"},"
            + "\"taskana.custom-int-3\": {\"value\":\"3\", \"type\":\"string\"},"
            + "\"taskana.custom-int-4\": {\"value\":\"4\", \"type\":\"string\"},"
            + "\"taskana.custom-int-5\": {\"value\":\"5\", \"type\":\"string\"},"
            + "\"taskana.custom-int-6\": {\"value\":\"6\", \"type\":\"string\"},"
            + "\"taskana.custom-int-7\": {\"value\":\"7\", \"type\":\"string\"},"
            + "\"taskana.custom-int-8\": {\"value\":\"8\", \"type\":\"string\"}"
            + "}";
    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", variables);
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    TaskSummary taskanaTask =
        taskService.createTaskQuery().externalIdIn(camundaTaskIds.get(0)).single();

    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_1)).isEqualTo(1);
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_2)).isEqualTo(2);
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_3)).isEqualTo(3);
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_4)).isEqualTo(4);
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_5)).isEqualTo(5);
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_6)).isEqualTo(6);
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_7)).isEqualTo(7);
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_8)).isEqualTo(8);
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_SetDefaultCustomIntegerInTaskanaTask_When_CamundaTaskHasDefaultCustomInteger()
      throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    TaskSummary taskanaTask =
        taskService.createTaskQuery().externalIdIn(camundaTaskIds.get(0)).single();

    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_1)).isNull();
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_2)).isNull();
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_3)).isNull();
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_4)).isNull();
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_5)).isNull();
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_6)).isNull();
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_7)).isNull();
    assertThat(taskanaTask.getCustomIntField(TaskCustomIntField.CUSTOM_INT_8)).isNull();
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void
      should_CreateTaskanaTaskWithComplexVariablesInCustomAttributes_When_StartCamundaTaskWithTheseVariables()
          throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_with_big_complex_variables_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    List<TaskSummary> taskanaTasks =
        this.taskService.createTaskQuery().externalIdIn(camundaTaskIds.get(0)).list();
    assertThat(taskanaTasks).hasSize(1);

    TaskSummary taskanaTaskSummary = taskanaTasks.get(0);
    String taskanaTaskExternalId = taskanaTaskSummary.getExternalId();
    assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskIds.get(0));

    Task taskanaTask = this.taskService.getTask(taskanaTaskSummary.getId());
    Map<String, String> taskanaTaskCustomAttributes = taskanaTask.getCustomAttributeMap();
    String variablesKeyString = "camunda:attribute1";
    String taskanaVariablesString = taskanaTaskCustomAttributes.get(variablesKeyString);

    assertTrue(taskanaVariablesString.length() > 1500000);
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void
      should_CreateTaskanaTasksWithComplexVariablesInCustomAttributes_When_ParentExecutionOfCamundaTasksStarted()
          throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_multiple_user_tasks_with_complex_variables_process", "");

    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep(this.adapterTaskPollingInterval);

    assertThat(camundaTaskIds).hasSize(3);

    // complete first 3 parallel tasks, one of which starts another task after completion that will
    // be checked for the process variables
    camundaTaskIds.forEach(
        camundaTaskId -> this.camundaProcessengineRequester.completeTaskWithId(camundaTaskId));

    camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    assertThat(camundaTaskIds).hasSize(1);

    Thread.sleep(this.adapterTaskPollingInterval);

    String expectedComplexProcessVariable =
        "{\"type\":\"object\","
            + "\"value\":\""
            + "{\\\"stringField\\\":\\\"\\\\fForm feed \\\\b Backspace \\\\t Tab"
            + " \\\\\\\\Backslash \\\\n newLine \\\\r Carriage return \\\\\\\" DoubleQuote\\\","
            + "\\\"intField\\\":1,\\\"doubleField\\\":1.1,\\\"booleanField\\\":false,"
            + "\\\"processVariableTestObjectTwoField\\\":["
            + "{\\\"stringFieldObjectTwo\\\":\\\"stringValueObjectTwo\\\","
            + "\\\"intFieldObjectTwo\\\":2,\\\"doubleFieldObjectTwo\\\":2.2,"
            + "\\\"booleanFieldObjectTwo\\\":true,"
            + "\\\"dateFieldObjectTwo\\\":\\\"1970-01-01 13:12:11\\\"}]}\","
            + "\"valueInfo\":{\"objectTypeName\":\"pro.taskana.impl.ProcessVariableTestObject\","
            + "\"serializationDataFormat\":\"application/json\"}}";

    String expectedPrimitiveProcessVariable1 =
        "{\"type\":\"integer\",\"value\":5," + "\"valueInfo\":null}";

    String expectedPrimitiveProcessVariable2 =
        "{\"type\":\"boolean\",\"value\":true," + "\"valueInfo\":null}";
    camundaTaskIds.forEach(
        camundaTaskId -> {
          Map<String, String> customAttributes =
              retrieveCustomAttributesFromNewTaskanaTask(camundaTaskId);

          assertThat(
              expectedComplexProcessVariable,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute1")));
          assertThat(
              expectedPrimitiveProcessVariable1,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute2")));
          assertThat(
              expectedPrimitiveProcessVariable2,
              SameJSONAs.sameJSONAs(customAttributes.get("camunda:attribute3")));
        });
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_CreateMultipleTaskanaTasks_When_StartProcessInstanceWithMultipleExecutionsInCamunda()
      throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_multiple_execution_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);
    assertThat(camundaTaskIds).hasSize(3);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    for (String camundaTaskId : camundaTaskIds) {
      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertThat(taskanaTasks).hasSize(1);
      String taskanaTaskExternalId = taskanaTasks.get(0).getExternalId();
      assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);
    }
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_CreateTaskanaTask_When_SystemConnectorHasCorrectSystemEngineIdentifier()
      throws Exception {

    final Map<String, SystemConnector> originalSystemConnectors =
        new HashMap<>(adapterManager.getSystemConnectors());

    setSystemConnector("default");

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    for (String camundaTaskId : camundaTaskIds) {
      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertThat(taskanaTasks).hasSize(1);
      TaskSummary taskanaTaskSummary = taskanaTasks.get(0);
      String taskanaTaskExternalId = taskanaTaskSummary.getExternalId();
      assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);
      String businessProcessId = taskanaTaskSummary.getBusinessProcessId();
      assertThat(processInstanceId).isEqualTo(businessProcessId);
    }

    adapterManager.getSystemConnectors().clear();
    adapterManager.getSystemConnectors().putAll(originalSystemConnectors);
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void
      should_CreateTaskanaTasksWithDifferentDomains_When_StartProcessWithDifferentDomainsInCamunda()
          throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process_with_different_domains", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);
    assertThat(camundaTaskIds).hasSize(3);
    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    List<Pair<String, String>> variablesToTaskList =
        Arrays.asList(
            Pair.of("DOMAIN_A", camundaTaskIds.get(0)),
            Pair.of("DOMAIN_A", camundaTaskIds.get(1)),
            Pair.of("DOMAIN_B", camundaTaskIds.get(2)));

    for (Pair<String, String> variablesToTask : variablesToTaskList) {
      List<TaskSummary> taskanaTaskSummaryList =
          this.taskService.createTaskQuery().externalIdIn(variablesToTask.getRight()).list();
      assertThat(taskanaTaskSummaryList).hasSize(1);
      TaskSummary taskanaTaskSummary = taskanaTaskSummaryList.get(0);

      Task taskanaTask = taskService.getTask(taskanaTaskSummary.getId());
      assertThat(taskanaTask.getDomain()).isEqualTo(variablesToTask.getLeft());
    }

    this.camundaProcessengineRequester.completeTaskWithId(camundaTaskIds.get(2));
    camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);
    assertThat(camundaTaskIds).hasSize(3);
    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    List<TaskSummary> taskanaTaskSummaryList =
        this.taskService.createTaskQuery().externalIdIn(camundaTaskIds.get(2)).list();
    assertThat(taskanaTaskSummaryList).hasSize(1);
    TaskSummary taskanaTaskSummary = taskanaTaskSummaryList.get(0);
    Task taskanaTask = taskService.getTask(taskanaTaskSummary.getId());
    assertThat(taskanaTask.getDomain()).isEqualTo("DOMAIN_A");
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void should_NotCreateTaskanaTask_When_SystemConnectorHasIncorrectSystemEngineIdentifier()
      throws Exception {

    final Map<String, SystemConnector> originalSystemConnectors =
        new HashMap<>(adapterManager.getSystemConnectors());

    setSystemConnector("wrongIdentifier");

    assertThat(taskanaOutboxRequester.getAllEvents()).isEmpty();

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    assertThat(taskanaOutboxRequester.getAllEvents()).hasSize(1);

    Thread.sleep((this.adapterTaskPollingInterval * 2));

    for (String camundaTaskId : camundaTaskIds) {
      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertThat(taskanaTasks).isEmpty();
    }

    assertThat(taskanaOutboxRequester.getAllEvents()).hasSize(1);

    adapterManager.getSystemConnectors().clear();
    adapterManager.getSystemConnectors().putAll(originalSystemConnectors);
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"taskadmin"})
  @Test
  void
      should_CreateTaskanaTasksWithVariablesInCustomAttributes_When_StartProcessWithTheseDifferentVariablesInCamunda()
          throws Exception {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process_with_multiple_tasks_and_complex_variables", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);
    assertThat(camundaTaskIds).hasSize(3);
    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    List<Pair<List<String>, String>> variablesToTaskList =
        Arrays.asList(
            Pair.of(Collections.singletonList("camunda:attribute1"), camundaTaskIds.get(0)),
            Pair.of(
                Arrays.asList("camunda:attribute1", "camunda:attribute2"), camundaTaskIds.get(1)),
            Pair.of(
                Arrays.asList("camunda:attribute1", "camunda:attribute2", "camunda:attribute3"),
                camundaTaskIds.get(2)));

    for (Pair<List<String>, String> variablesToTask : variablesToTaskList) {
      List<TaskSummary> taskanaTaskSummaryList =
          this.taskService.createTaskQuery().externalIdIn(variablesToTask.getRight()).list();
      assertThat(taskanaTaskSummaryList).hasSize(1);
      TaskSummary taskanaTaskSummary = taskanaTaskSummaryList.get(0);

      Task taskanaTask = taskService.getTask(taskanaTaskSummary.getId());
      assertThat(taskanaTask.getCustomAttributeMap().keySet())
          .containsExactlyInAnyOrderElementsOf(variablesToTask.getLeft());
    }

    this.camundaProcessengineRequester.completeTaskWithId(camundaTaskIds.get(2));
    camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);
    assertThat(camundaTaskIds).hasSize(3);
    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    List<TaskSummary> taskanaTaskSummaryList =
        this.taskService.createTaskQuery().externalIdIn(camundaTaskIds.get(2)).list();
    assertThat(taskanaTaskSummaryList).hasSize(1);
    TaskSummary taskanaTaskSummary = taskanaTaskSummaryList.get(0);
    Task taskanaTask = taskService.getTask(taskanaTaskSummary.getId());
    assertThat(taskanaTask.getCustomAttributeMap().keySet())
        .containsExactlyInAnyOrderElementsOf(
            Arrays.asList("camunda:attribute1", "camunda:attribute2"));
  }

  @WithAccessId(
      user = "teamlead_1",
      groups = {"admin"})
  @Test
  void should_SetPlannedDateInTaskanaTask_When_StartCamundaTaskWithFollowUpDate() throws Exception {
    final Instant now = Instant.now();
    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process_with_plannedDate", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterCompletionPollingInterval * 1.2));

    // Make sure we only have one Camunda Task, so we don't need a for-loop
    assertThat(camundaTaskIds.size()).isEqualTo(1);
    String camundaTaskId = camundaTaskIds.get(0);
    // retrieve and check taskanaTaskId
    List<TaskSummary> taskanaTaskSummaryList =
        this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
    assertThat(taskanaTaskSummaryList.size()).isEqualTo(1);
    String taskanaTaskExternalId = taskanaTaskSummaryList.get(0).getExternalId();
    assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);
    String taskanaTaskId = taskanaTaskSummaryList.get(0).getId();
    Task taskanaTask = this.taskService.getTask(taskanaTaskId);
    // Check if followUp Date from Camunda task is equal to plannedDate from Taskana task
    Instant expectedDate = DateTimeUtil.parseDateTime("2015-06-26T09:54:00").toDate().toInstant();
    assertThat(taskanaTask.getPlanned()).isEqualTo(expectedDate);

    this.camundaProcessengineRequester.completeTaskWithId(camundaTaskId);
    camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);
    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    // Make sure we only have one Camunda Task, so we don't need a for-loop
    assertThat(camundaTaskIds).hasSize(1);
    camundaTaskId = camundaTaskIds.get(0);
    taskanaTaskSummaryList = this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
    assertThat(taskanaTaskSummaryList.size()).isEqualTo(1);
    taskanaTaskExternalId = taskanaTaskSummaryList.get(0).getExternalId();
    assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);
    taskanaTaskId = taskanaTaskSummaryList.get(0).getId();
    taskanaTask = this.taskService.getTask(taskanaTaskId);
    // Check if plannedDate was set to Instant.now during setTimestampsInTaskanaTask() method call.
    // This is the desired behaviour since no followUpDate is set in this Camunda Task.
    assertThat(taskanaTask.getPlanned()).isAfter(now);
  }

  private void setSystemConnector(String systemEngineIdentifier) {

    StringTokenizer systemConfigParts = new StringTokenizer(configuredSystemConnectorUrls, "|");
    SystemUrlInfo systemUrlInfo = new SystemUrlInfo();
    systemUrlInfo.setCamundaEngineIdentifier(systemEngineIdentifier);
    systemUrlInfo.setSystemRestUrl(systemConfigParts.nextToken().trim());
    systemUrlInfo.setSystemTaskEventUrl(systemConfigParts.nextToken().trim());

    SystemConnector systemConnector = new CamundaSystemConnectorImpl(systemUrlInfo);

    Map<String, SystemConnector> systemConnectors = adapterManager.getSystemConnectors();
    systemConnectors.clear();

    systemConnectors.put(systemUrlInfo.getSystemRestUrl(), systemConnector);
  }

  private Map<String, String> retrieveCustomAttributesFromNewTaskanaTask(String camundaTaskId) {

    Map<String, String> customAttributes = new HashMap<>();
    try {

      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertThat(taskanaTasks).hasSize(1);
      TaskSummary taskanaTaskSummary = taskanaTasks.get(0);
      String taskanaTaskExternalId = taskanaTaskSummary.getExternalId();
      assertThat(taskanaTaskExternalId).isEqualTo(camundaTaskId);

      // get the actual task instead of summary to access custom attributes
      Task taskanaTask = this.taskService.getTask(taskanaTaskSummary.getId());

      customAttributes = taskanaTask.getCustomAttributeMap();
      return customAttributes;

    } catch (TaskNotFoundException | NotAuthorizedException e) {
      LOGGER.info(
          "Caught Exception while trying to retrieve custom attributes from new taskana task", e);
    }
    return customAttributes;
  }
}
