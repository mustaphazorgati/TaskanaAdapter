package pro.taskana.adapter.systemconnector.camunda.api.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import pro.taskana.adapter.camunda.outbox.rest.resource.CamundaTaskEventResource;
import pro.taskana.adapter.systemconnector.api.ReferencedTask;

@Component
public class CamundaTaskRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(CamundaTaskRetriever.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    public List<ReferencedTask> retrieveNewStartedCamundaTasks(String camundaSystemTaskEventUrl) {

        LOGGER.debug("### entry to retrieveNewStartedCamundaTasks. createdAfter = {} ###");

        CamundaTaskEventResource[] camundaTaskEventResources = getCamundaTaskEventResources(camundaSystemTaskEventUrl,
            CamundaSystemConnectorImpl.URL_GET_CAMUNDA_CREATE_EVENTS);

        List<ReferencedTask> referencedTasks = getReferencedTasksFromCamundaTaskEventResources(
            camundaTaskEventResources);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("exit from retrieveActiveCamundaTasks. Retrieved Tasks: {}", referencedTasks);
        }
        return referencedTasks;
    }

    private CamundaTaskEventResource[] getCamundaTaskEventResources(String camundaSystemTaskEventUrl,
        String eventSelector) {

        String requestUrl = camundaSystemTaskEventUrl + CamundaSystemConnectorImpl.URL_OUTBOX_REST_PATH
            + eventSelector;

        LOGGER.debug("### retrieving camunda task event resources with url {} ###", requestUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<CamundaTaskEventResource[]> responseEntity = restTemplate.exchange(
            requestUrl, HttpMethod.GET, new HttpEntity<Object>(headers),
            CamundaTaskEventResource[].class);

        CamundaTaskEventResource[] camundaTaskEventResources = responseEntity.getBody();

        return camundaTaskEventResources;
    }

    private List<ReferencedTask> getReferencedTasksFromCamundaTaskEventResources(
        CamundaTaskEventResource[] camundaTaskEventResources) {

        List<ReferencedTask> referencedTasks = new ArrayList<>();

        for (CamundaTaskEventResource camundaTaskEventResource : camundaTaskEventResources) {

            String referencedTaskJson = camundaTaskEventResource.getPayload();

            try {

                ReferencedTask referencedTask = objectMapper.readValue(referencedTaskJson, ReferencedTask.class);
                referencedTask.setOutboxEventId(String.valueOf(camundaTaskEventResource.getId()));
                referencedTask.setOutboxEventType(String.valueOf(camundaTaskEventResource.getType()));
                referencedTasks.add(referencedTask);

            } catch (IOException e) {

                LOGGER.warn(
                    "Caught {} while trying to create ReferencedTasks out of CamundaTaskEventResources. RefTaskJson = {}",
                    e, referencedTaskJson);
            }
        }
        return referencedTasks;
    }

    public List<ReferencedTask> retrieveTerminatedCamundaTasks(String camundaSystemURL) {
        LOGGER.debug("entry to retrieveFinishedCamundaTasks. CamundSystemURL = {} ", camundaSystemURL);

        CamundaTaskEventResource[] camundaTaskEventResources = getCamundaTaskEventResources(camundaSystemURL,
            CamundaSystemConnectorImpl.URL_GET_CAMUNDA_COMPLETE_EVENTS);

        List<ReferencedTask> referencedTasks = getReferencedTasksFromCamundaTaskEventResources(
            camundaTaskEventResources);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("exit from retrieveTerminatedCamundaTasks. Retrieved Tasks: {}", referencedTasks);
        }
        return referencedTasks;

    }
}
