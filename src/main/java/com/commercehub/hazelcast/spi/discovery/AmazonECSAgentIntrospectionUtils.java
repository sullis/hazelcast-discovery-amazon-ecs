/*
 * Copyright (C) 2017 Commerce Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commercehub.hazelcast.spi.discovery;

import com.amazonaws.AmazonClientException;
import com.amazonaws.SdkClientException;
import com.amazonaws.internal.EC2CredentialsUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for retrieving Amazon ECS Agent Introspection data.<br>
 *
 * More information about Amazon ECS Agent Introspection
 *
 * @see <a
 *      href="http://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-agent-introspection.html">Amazon
 *      EC2 Container Service Developer Guide: Amazon ECS Container Agent Introspection</a>
 */
public class AmazonECSAgentIntrospectionUtils {

    /**
     * System property for overriding the Amazon EC2 Instance Metadata Service
     * endpoint.
     */
    public static final String ECS_AGENT_INTROSPECTION_API_OVERRIDE_SYSTEM_PROPERTY =
            "com.commercehub.amazonaws.util.ecsAgentIntrospectionAPIEndpointOverride";

    /** Default endpoint for the Amazon ECS Agent Introspection API. */
    private static final String ECS_AGENT_INTROSPECTION_API_URL = "http://172.17.0.1:51678";
    private static final String ECS_METADATA_ROOT = "/v1/metadata";
    private static final String ECS_TASKS_ROOT = "/v1/tasks";

    private static final int DEFAULT_QUERY_RETRIES = 3;
    private static final int MINIMUM_RETRY_WAIT_TIME_MILLISECONDS = 250;

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.PASCAL_CASE_TO_CAMEL_CASE);
    }

    private static final Log log = LogFactory.getLog(AmazonECSAgentIntrospectionUtils.class);

    public static Metadata getMetadata() {
        String json = getData(ECS_METADATA_ROOT);
        if (null == json) {
            return null;
        }

        try {
            return mapper.readValue(json, Metadata.class);
        } catch (Exception e) {
            log.warn("Unable to parse ECS Agent Metadata (" + json + "): " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get information about an ECS Task running on the local container instance identified by the ID of the Docker
     * container running for that Task. Both long- and short-form Docker container IDs are supported.
     *
     * @param dockerId the long- or short-form ID of the Docker container running for the Task to be retrieved
     * @return information about the ECS Task identified by the provided dockerId
     */
    public static Task getTask(String dockerId) {
        String json = getData(ECS_TASKS_ROOT + "?dockerid=" + dockerId);
        if (null == json) {
            return null;
        }

        try {
            return mapper.readValue(json, Task.class);
        } catch (Exception e) {
            log.warn("Unable to parse ECS Agent Task (" + json + "): " + e.getMessage(), e);
            return null;
        }
    }

    public static String getData(String path) {
        return getData(path, DEFAULT_QUERY_RETRIES);
    }

    public static String getData(String path, int tries) {
        List<String> items = getItems(path, tries, true);
        if (null != items && items.size() > 0) {
            return items.get(0);
        }
        return null;
    }

    private static List<String> getItems(String path, int tries, boolean slurp) {
        if (tries == 0) {
            throw new SdkClientException(
                    "Unable to contact ECS Agent Introspection API.");
        }

        List<String> items;
        try {
            String hostAddress = getHostAddressForECSAgentIntrospectionAPI();
            String response = EC2CredentialsUtils.getInstance().readResource(new URI(hostAddress + path));
            if (slurp) {
                items = Collections.singletonList(response);
            } else {
                items = Arrays.asList(response.split("\n"));
            }
            return items;
        } catch (AmazonClientException ace) {
            log.warn("Unable to retrieve the requested metadata.");
            return null;
        } catch (Exception e) {
            // Retry on any other exceptions
            int pause = (int) (Math.pow(2, DEFAULT_QUERY_RETRIES - tries) * MINIMUM_RETRY_WAIT_TIME_MILLISECONDS);
            try {
                Thread.sleep(pause < MINIMUM_RETRY_WAIT_TIME_MILLISECONDS ? MINIMUM_RETRY_WAIT_TIME_MILLISECONDS
                        : pause);
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
            return getItems(path, tries - 1, slurp);
        }
    }

    public static String getHostAddressForECSAgentIntrospectionAPI() {
        String host = System.getProperty(ECS_AGENT_INTROSPECTION_API_OVERRIDE_SYSTEM_PROPERTY);
        return host != null ? host : ECS_AGENT_INTROSPECTION_API_URL;
    }

    public static class Metadata {

        private final String cluster;
        private final String containerInstanceArn;
        private final String version;

        @JsonCreator
        public Metadata(
                @JsonProperty(value = "Cluster") String cluster,
                @JsonProperty(value = "ContainerInstanceArn") String containerInstanceArn,
                @JsonProperty(value = "Version") String version) {

            this.cluster = cluster;
            this.containerInstanceArn = containerInstanceArn;
            this.version = version;
        }

        public String getCluster() {
            return cluster;
        }

        public String getContainerInstanceArn() {
            return containerInstanceArn;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            if (getCluster() != null) {
                sb.append("Cluster: ").append(getCluster()).append(",");
            }
            if (getContainerInstanceArn() != null) {
                sb.append("ContainerInstanceArn: ").append(getContainerInstanceArn()).append(",");
            }
            if (getVersion() != null) {
                sb.append("Version: ").append(getVersion());
            }
            sb.append("}");
            return sb.toString();
        }

    }

    public static class Task {

        private final String arn;
        private final String desiredStatus;
        private final String knownStatus;
        private final String family;
        private final String version;
        private final List<Container> containers;

        @JsonCreator
        public Task(
                @JsonProperty(value = "Arn") String arn,
                @JsonProperty(value = "DesiredStatus") String desiredStatus,
                @JsonProperty(value = "KnownStatus") String knownStatus,
                @JsonProperty(value = "Family") String family,
                @JsonProperty(value = "Version") String version,
                @JsonProperty(value = "Containers") List<Container> containers) {

            this.arn = arn;
            this.desiredStatus = desiredStatus;
            this.knownStatus = knownStatus;
            this.family = family;
            this.version = version;

            if (containers != null) {
                this.containers = new ArrayList<>(containers);
            } else {
                this.containers = Collections.emptyList();
            }
        }

        public String getArn() {
            return arn;
        }

        public String getDesiredStatus() {
            return desiredStatus;
        }

        public String getKnownStatus() {
            return knownStatus;
        }

        public String getFamily() {
            return family;
        }

        public String getVersion() {
            return version;
        }

        public List<Container> getContainers() {
            return Collections.unmodifiableList(containers);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            if (getArn() != null) {
                sb.append("Arn: ").append(getArn()).append(",");
            }
            if (getDesiredStatus() != null) {
                sb.append("DesiredStatus: ").append(getDesiredStatus()).append(",");
            }
            if (getKnownStatus() != null) {
                sb.append("KnownStatus: ").append(getKnownStatus()).append(",");
            }
            if (getFamily() != null) {
                sb.append("Family: ").append(getFamily()).append(",");
            }
            if (getVersion() != null) {
                sb.append("Version: ").append(getVersion()).append(",");
            }
            if (getContainers() != null) {
                sb.append("Containers: ").append(getContainers());
            }
            sb.append("}");
            return sb.toString();
        }

    }

    public static class Container {

        private final String dockerId;
        private final String dockerName;
        private final String name;

        @JsonCreator
        public Container(
                @JsonProperty(value = "DockerId") String dockerId,
                @JsonProperty(value = "DockerName") String dockerName,
                @JsonProperty(value = "Name") String name) {

            this.dockerId = dockerId;
            this.dockerName = dockerName;
            this.name = name;
        }

        public String getDockerId() {
            return dockerId;
        }

        public String getDockerName() {
            return dockerName;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            if (getDockerId() != null) {
                sb.append("DockerId: ").append(getDockerId()).append(",");
            }
            if (getDockerName() != null) {
                sb.append("DockerName: ").append(getDockerName()).append(",");
            }
            if (getName() != null) {
                sb.append("Name: ").append(getName());
            }
            sb.append("}");
            return sb.toString();
        }

    }

}
