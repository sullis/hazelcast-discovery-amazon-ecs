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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.util.EC2MetadataUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { AmazonECSDiscoveryUtils.class, AmazonECSAgentIntrospectionUtils.class, EC2MetadataUtils.class })
public class AmazonECSDiscoveryUtilsTest {

    private static final String ECS_AGENT_VERSION = "Amazon ECS Agent - v1.14.3 (15de319)";
    private static final String ECS_CLUSTER_NAME = "default-ecs-cluster-Cluster-1XC1HAMRI9XVK";
    private static final String CONTAINER_INSTANCE_ARN = "arn:aws:ecs:us-east-1:10000000000:container-instance/f973c8d6-336f-4f8e-9160-4f6d9b4f53a8";
    private static final String CONTAINER_INSTANCE_PRIVATE_IP_ADDRESS = "172.25.70.10";
    private static final String ECS_SERVICE_NAME = "foo-service";
    private static final String ECS_TASK_GROUP_NAME = String.format("service:%s", ECS_SERVICE_NAME);
    private static final String TASK_ARN = "arn:aws:ecs:us-east-1:10000000000:task/5c26ebf5-56ae-4121-9f90-28a5f1295851";
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_FAMILY = "service-TaskDefinition-KLW9WAUEMIX1";
    private static final String TASK_VERSION = "1";
    private static final String DOCKER_SHORT_ID = "c63e3a0c7b25";
    private static final String DOCKER_LONG_ID = "c63e3a0c7b25393c743d6b1552806e36066bbe656f80130a315286fa48e6b003";
    private static final String AGENT_CONTAINER_DOCKER_NAME = "ecs-service-TaskDefinition-KLW9WAUEMIX1-1-foo-8a979cfbd4c3fdb28301";
    private static final String CONTAINER_NAME = "foo";
    private static final int HOST_PORT = 32863;
    private static final int CONTAINER_PORT = 5701;

    @Test
    public void discoversECSClusterName() throws ClusterNameDiscoveryException {
        AmazonECSAgentIntrospectionUtils.Metadata agentMetadata = new AmazonECSAgentIntrospectionUtils.Metadata(
                ECS_CLUSTER_NAME,
                CONTAINER_INSTANCE_ARN,
                ECS_AGENT_VERSION);

        mockStatic(AmazonECSAgentIntrospectionUtils.class);
        when(AmazonECSAgentIntrospectionUtils.getMetadata()).thenReturn(agentMetadata);

        AmazonECS amazonECS = mock(AmazonECS.class);
        AmazonECSDiscoveryUtils amazonECSDiscoveryUtils = new AmazonECSDiscoveryUtils(amazonECS);

        assertEquals(amazonECSDiscoveryUtils.discoverClusterName(), ECS_CLUSTER_NAME);
    }

    @Test
    public void discoversServiceName() throws UnknownHostException, ServiceNameDiscoveryException {
        InetAddress inetAddress = mock(InetAddress.class);
        when(inetAddress.getHostName()).thenReturn(DOCKER_SHORT_ID);

        mockStatic(InetAddress.class);
        when(InetAddress.getLocalHost()).thenReturn(inetAddress);

        AmazonECSAgentIntrospectionUtils.Container agentContainer = new AmazonECSAgentIntrospectionUtils.Container(
                DOCKER_LONG_ID,
                AGENT_CONTAINER_DOCKER_NAME,
                CONTAINER_NAME);

        List<AmazonECSAgentIntrospectionUtils.Container> agentContainers = new ArrayList<>();
        agentContainers.add(agentContainer);

        AmazonECSAgentIntrospectionUtils.Task agentTask = new AmazonECSAgentIntrospectionUtils.Task(
                TASK_ARN,
                TASK_STATUS_RUNNING,
                TASK_STATUS_RUNNING,
                TASK_FAMILY,
                TASK_VERSION,
                agentContainers);

        mockStatic(AmazonECSAgentIntrospectionUtils.class);
        when(AmazonECSAgentIntrospectionUtils.getTask(anyString())).thenReturn(agentTask);

        Container ecsContainer = new Container().withName(CONTAINER_NAME);

        Task ecsTask = new Task().withContainers(ecsContainer).withGroup(ECS_TASK_GROUP_NAME);
        DescribeTasksResult describeTasksResult = new DescribeTasksResult().withTasks(ecsTask);

        AmazonECS amazonECS = mock(AmazonECS.class);
        when(amazonECS.describeTasks(any(DescribeTasksRequest.class))).thenReturn(describeTasksResult);

        AmazonECSDiscoveryUtils amazonECSDiscoveryUtils = new AmazonECSDiscoveryUtils(amazonECS);

        //noinspection ConstantConditions
        assertEquals(amazonECSDiscoveryUtils.discoverServiceName(ECS_CLUSTER_NAME).get(), ECS_SERVICE_NAME);
    }

    @Test
    public void discoversPublicHazelcastAddress()
            throws UnknownHostException, PublicHazelcastAddressDiscoveryException {

        mockStatic(EC2MetadataUtils.class);
        when(EC2MetadataUtils.getPrivateIpAddress()).thenReturn(CONTAINER_INSTANCE_PRIVATE_IP_ADDRESS);

        InetAddress inetAddress = mock(InetAddress.class);
        when(inetAddress.getHostName()).thenReturn(DOCKER_SHORT_ID);

        mockStatic(InetAddress.class);
        when(InetAddress.getLocalHost()).thenReturn(inetAddress);

        AmazonECSAgentIntrospectionUtils.Metadata agentMetadata = new AmazonECSAgentIntrospectionUtils.Metadata(
                ECS_CLUSTER_NAME,
                CONTAINER_INSTANCE_ARN,
                ECS_AGENT_VERSION);

        AmazonECSAgentIntrospectionUtils.Container agentContainer = new AmazonECSAgentIntrospectionUtils.Container(
                DOCKER_LONG_ID,
                AGENT_CONTAINER_DOCKER_NAME,
                CONTAINER_NAME);

        List<AmazonECSAgentIntrospectionUtils.Container> agentContainers = new ArrayList<>();
        agentContainers.add(agentContainer);

        AmazonECSAgentIntrospectionUtils.Task agentTask = new AmazonECSAgentIntrospectionUtils.Task(
                TASK_ARN,
                TASK_STATUS_RUNNING,
                TASK_STATUS_RUNNING,
                TASK_FAMILY,
                TASK_VERSION,
                agentContainers);

        mockStatic(AmazonECSAgentIntrospectionUtils.class);
        when(AmazonECSAgentIntrospectionUtils.getMetadata()).thenReturn(agentMetadata);
        when(AmazonECSAgentIntrospectionUtils.getTask(anyString())).thenReturn(agentTask);

        NetworkBinding ecsNetworkBinding = new NetworkBinding()
                .withHostPort(HOST_PORT)
                .withContainerPort(CONTAINER_PORT);

        Container ecsContainer = new Container().withName(CONTAINER_NAME).withNetworkBindings(ecsNetworkBinding);

        Task ecsTask = new Task().withContainers(ecsContainer);
        DescribeTasksResult describeTasksResult = new DescribeTasksResult().withTasks(ecsTask);

        AmazonECS amazonECS = mock(AmazonECS.class);
        when(amazonECS.describeTasks(any(DescribeTasksRequest.class))).thenReturn(describeTasksResult);

        AmazonECSDiscoveryUtils amazonECSDiscoveryUtils = new AmazonECSDiscoveryUtils(amazonECS);

        assertEquals(amazonECSDiscoveryUtils.discoverPublicHazelcastAddress(CONTAINER_PORT),
                String.format("%s:%s", CONTAINER_INSTANCE_PRIVATE_IP_ADDRESS, HOST_PORT));
    }

}
