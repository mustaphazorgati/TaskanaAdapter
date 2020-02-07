package pro.taskana.adapter.integration;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ContextConfiguration;
import uk.co.datumedge.hamcrest.json.SameJSONAs;

import pro.taskana.adapter.test.TaskanaAdapterTestApplication;
import pro.taskana.common.api.exceptions.NotAuthorizedException;
import pro.taskana.security.JaasRunner;
import pro.taskana.security.WithAccessId;
import pro.taskana.task.api.Task;
import pro.taskana.task.api.TaskSummary;
import pro.taskana.task.api.exceptions.TaskNotFoundException;


/** Test class to test the conversion of tasks generated by Camunda BPM to Taskana tasks. */
@SpringBootTest(
    classes = TaskanaAdapterTestApplication.class,
    webEnvironment = WebEnvironment.DEFINED_PORT)
@AutoConfigureWebTestClient
@RunWith(JaasRunner.class)
@ContextConfiguration
@SuppressWarnings("checkstyle:LineLength")
public class TestTaskAcquisition extends AbsIntegrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskAcquisition.class);

  @WithAccessId(
      userName = "teamlead_1",
      groupNames = {"admin"})
  @Test
  public void user_task_process_instance_started_in_camunda_via_rest_should_result_in_taskanaTask()
      throws JSONException, InterruptedException {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    for (String camundaTaskId : camundaTaskIds) {
      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertEquals(1, taskanaTasks.size());
      String taskanaTaskExternalId = taskanaTasks.get(0).getExternalId();
      assertEquals(taskanaTaskExternalId, camundaTaskId);
    }
  }

  @WithAccessId(
      userName = "teamlead_1",
      groupNames = {"admin"})
  @Test
  public void
      multiple_user_task_process_instances_started_in_camunda_via_rest_should_result_in_multiple_taskanaTasks()
          throws JSONException, InterruptedException {

    int numberOfProcesses = 10;
    List<List<String>> camundaTaskIdsList = new ArrayList<List<String>>();
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
        assertEquals(1, taskanaTasks.size());
        String taskanaTaskExternalId = taskanaTasks.get(0).getExternalId();
        assertEquals(taskanaTaskExternalId, camundaTaskId);
      }
    }
  }

  @WithAccessId(
      userName = "teamlead_1",
      groupNames = {"admin"})
  @Test
  public void
      task_with_primitive_variables_should_result_in_taskanaTask_with_those_variables_in_custom_attributes()
          throws JSONException, InterruptedException {

    String variables =
        "\"variables\": {\"amount\": {\"value\":555, "
            + "\"type\":\"long\"},\"item\": {\"value\": \"item-xyz\"}}";
    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_process", variables);
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    String assumedVariablesString =
        "{\"amount\":{\"type\":\"Long\",\"value\":555,\"valueInfo\":"
            + "{\"objectTypeName\":\"java.lang.Long\"}},"
            + "\"item\":{\"type\":\"Object\","
            + "\"value\":\"\\\"item-xyz\\\"\","
            + "\"valueInfo\":{\"objectTypeName\":\"java.lang.String\","
            + "\"serializationDataFormat\":\"application/json\"}}}";

    camundaTaskIds.forEach(
        camundaTaskId ->
            retrieveTaskanaTaskAndVerifyTaskVariables(camundaTaskId, assumedVariablesString));
  }

  @WithAccessId(
      userName = "teamlead_1",
      groupNames = {"admin"})
  @Test
  public void
      task_with_complex_variables_should_result_in_taskanaTask_with_those_variables_in_custom_attributes()
          throws JSONException, InterruptedException {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_user_task_with_complex_variables_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    String assumedVariablesString =
        "{\"attribute1\":{\"type\":\"Object\","
            + "\"value\":\""
            + "{\\\"stringField\\\":\\\"\\\\fForm feed \\\\b Backspace \\\\t Tab"
            + " \\\\\\\\Backslash \\\\n newLine \\\\r Carriage return \\\\\\\" DoubleQuote\\\","
            + "\\\"intField\\\":1,\\\"doubleField\\\":1.1,\\\"booleanField\\\":false,"
            + "\\\"processVariableTestObjectTwoField\\\":"
            + "{\\\"stringFieldObjectTwo\\\":\\\"stringValueObjectTwo\\\","
            + "\\\"intFieldObjectTwo\\\":2,\\\"doubleFieldObjectTwo\\\":2.2,"
            + "\\\"booleanFieldObjectTwo\\\":true,"
            + "\\\"dateFieldObjectTwo\\\":\\\"1970-01-01 13:12:11\\\"}}\","
            + "\"valueInfo\":{\"objectTypeName\":\"pro.taskana.impl.ProcessVariableTestObject\","
            + "\"serializationDataFormat\":\"application/json\"}},"
            + "\"attribute2\":{\"type\":\"Integer\",\"value\":5,"
            + "\"valueInfo\":{\"objectTypeName\":\"java.lang.Integer\"}},"
            + "\"attribute3\":{\"type\":\"Boolean\",\"value\":true,"
            + "\"valueInfo\":{\"objectTypeName\":\"java.lang.Boolean\"}}}";

    camundaTaskIds.forEach(
        camundaTaskId ->
            retrieveTaskanaTaskAndVerifyTaskVariables(camundaTaskId, assumedVariablesString));
  }

  @WithAccessId(
      userName = "teamlead_1",
      groupNames = {"admin"})
  @Test
  public void
      task_with_complex_variables_from_parent_execution_should_result_in_taskanaTasks_with_those_variables_in_custom_attributes()
          throws JSONException, InterruptedException {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_multiple_user_tasks_with_complex_variables_process", "");

    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    Thread.sleep(this.adapterTaskPollingInterval);

    assertEquals(3, camundaTaskIds.size());

    // complete first 3 parallel tasks, one of which starts another task after completion that will
    // be checked for the process variables
    camundaTaskIds.forEach(
        camundaTaskId -> this.camundaProcessengineRequester.completeTaskWithId(camundaTaskId));

    camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);

    assertEquals(1, camundaTaskIds.size());

    Thread.sleep(this.adapterTaskPollingInterval);

    String assumedVariablesString =
        "{\"attribute1\":{\"type\":\"Object\","
            + "\"value\":\""
            + "{\\\"stringField\\\":\\\"\\\\fForm feed \\\\b Backspace \\\\t Tab"
            + " \\\\\\\\Backslash \\\\n newLine \\\\r Carriage return \\\\\\\" DoubleQuote\\\","
            + "\\\"intField\\\":1,\\\"doubleField\\\":1.1,\\\"booleanField\\\":false,"
            + "\\\"processVariableTestObjectTwoField\\\":"
            + "{\\\"stringFieldObjectTwo\\\":\\\"stringValueObjectTwo\\\","
            + "\\\"intFieldObjectTwo\\\":2,\\\"doubleFieldObjectTwo\\\":2.2,"
            + "\\\"booleanFieldObjectTwo\\\":true,"
            + "\\\"dateFieldObjectTwo\\\":\\\"1970-01-01 13:12:11\\\"}}\","
            + "\"valueInfo\":{\"objectTypeName\":\"pro.taskana.impl.ProcessVariableTestObject\","
            + "\"serializationDataFormat\":\"application/json\"}},"
            + "\"attribute2\":{\"type\":\"Integer\",\"value\":5,"
            + "\"valueInfo\":{\"objectTypeName\":\"java.lang.Integer\"}},"
            + "\"attribute3\":{\"type\":\"Boolean\",\"value\":true,"
            + "\"valueInfo\":{\"objectTypeName\":\"java.lang.Boolean\"}}}";

    camundaTaskIds.forEach(
        camundaTaskId ->
            retrieveTaskanaTaskAndVerifyTaskVariables(camundaTaskId, assumedVariablesString));
  }

  @WithAccessId(
      userName = "teamlead_1",
      groupNames = {"admin"})
  @Test
  public void process_instance_with_multiple_executions_should_result_in_multiple_taskanaTasks()
      throws JSONException, InterruptedException {

    String processInstanceId =
        this.camundaProcessengineRequester.startCamundaProcessAndReturnId(
            "simple_multiple_execution_process", "");
    List<String> camundaTaskIds =
        this.camundaProcessengineRequester.getTaskIdsFromProcessInstanceId(processInstanceId);
    assertEquals(3, camundaTaskIds.size());

    Thread.sleep((long) (this.adapterTaskPollingInterval * 1.2));

    for (String camundaTaskId : camundaTaskIds) {
      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertEquals(1, taskanaTasks.size());
      String taskanaTaskExternalId = taskanaTasks.get(0).getExternalId();
      assertEquals(taskanaTaskExternalId, camundaTaskId);
    }
  }

  private void retrieveTaskanaTaskAndVerifyTaskVariables(
      String camundaTaskId, String assumedVariablesString) {

    try {

      List<TaskSummary> taskanaTasks =
          this.taskService.createTaskQuery().externalIdIn(camundaTaskId).list();
      assertEquals(1, taskanaTasks.size());
      TaskSummary taskanaTaskSummary = taskanaTasks.get(0);
      String taskanaTaskExternalId = taskanaTaskSummary.getExternalId();
      assertEquals(taskanaTaskExternalId, camundaTaskId);

      // get the actual task instead of summary to access custom attributes
      Task taskanaTask = this.taskService.getTask(taskanaTaskSummary.getId());
      Map<String, String> taskanaTaskCustomAttributes = taskanaTask.getCustomAttributes();
      String variablesKeyString = "referenced_task_variables";
      String taskanaVariablesString = taskanaTaskCustomAttributes.get(variablesKeyString);

      // attention: the order of "amount" and "item" returned by camunda is random. Therefore, a
      // simple
      // comparison of the strings doesn't work.
      // rather use SameJSONAs.sameJSONAs from hamcrest-json to compare Json strings independent of
      // child order
      Assert.assertThat(assumedVariablesString, SameJSONAs.sameJSONAs(taskanaVariablesString));

    } catch (TaskNotFoundException | NotAuthorizedException e) {
      LOGGER.info("Caught {}, while trying to create a taskana task and verify its variables", e, e);
    }
  }
}