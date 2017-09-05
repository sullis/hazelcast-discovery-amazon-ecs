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

package com.commercehub.hazelcast.spi.discovery

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.ecs.model.Container
import com.amazonaws.services.ecs.model.ContainerInstance
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult
import com.amazonaws.services.ecs.model.DescribeTasksResult
import com.amazonaws.services.ecs.model.ListTasksRequest
import com.amazonaws.services.ecs.model.ListTasksResult
import com.amazonaws.services.ecs.model.NetworkBinding
import com.amazonaws.services.ecs.model.Task
import com.hazelcast.logging.ILogger
import com.hazelcast.spi.discovery.DiscoveryNode
import spock.lang.Specification

class AmazonECSDiscoveryStrategySpec extends Specification {

    AmazonECSDiscoveryStrategy strategy

    def logger
    def mockECSClient
    def mockEC2Client
    def containerPort

    def setup() {
        logger = Mock(ILogger)
        mockECSClient = Mock(AmazonECSClient)
        mockEC2Client = Mock(AmazonEC2Client)
        containerPort = 5701

        strategy = new AmazonECSDiscoveryStrategy(logger,
                Collections.emptyMap(),
                mockECSClient, mockEC2Client, containerPort)
    }

    def setupMocksForContainer(String instanceIpAddress, int hostPort) {
        Task task = Mock(Task)
        Container container = Mock(Container)
        ContainerInstance containerInstance = Mock(ContainerInstance)
        Instance instance = Mock(Instance)
        NetworkBinding networkBinding = Mock(NetworkBinding)
        Reservation reservation = Mock(Reservation)

        ListTasksResult listTasksResult = Mock(ListTasksResult)
        mockECSClient.listTasks(_) >> listTasksResult
        listTasksResult.getTaskArns() >> ["arn"]

        DescribeTasksResult describeTasksResult = Mock(DescribeTasksResult)
        mockECSClient.describeTasks(_) >> describeTasksResult
        describeTasksResult.getTasks() >> [task]

        DescribeContainerInstancesResult describeContainerInstancesResult = Mock(DescribeContainerInstancesResult)
        mockECSClient.describeContainerInstances(_) >> describeContainerInstancesResult
        describeContainerInstancesResult.getContainerInstances() >> [containerInstance]

        DescribeInstancesResult describeInstancesResult = Mock(DescribeInstancesResult)
        mockEC2Client.describeInstances(_) >> describeInstancesResult
        describeInstancesResult.getReservations() >> [reservation]

        task.getContainerInstanceArn() >> "arn" // todo: not needed?
        task.getContainers() >> [container]
        container.getNetworkBindings() >> [networkBinding]
        networkBinding.getContainerPort() >> containerPort
        networkBinding.getHostPort() >> hostPort
        reservation.getInstances() >> [instance]
        instance.getPrivateIpAddress() >> instanceIpAddress
    }

    def "discoverNodes() - happy path"() {
        given: "task stuff"
        setupMocksForContainer("123.4.6.32", 56789)

        when:
        Iterable<DiscoveryNode> nodes = strategy.discoverNodes()

        then:
        !nodes.asList().empty
        nodes.asList().get(0).privateAddress.inetAddress.hostAddress == "123.4.6.32"
        nodes.asList().get(0).privateAddress.port == 56789
    }

    def "discoverNodes() - no nodes doesn't explode"() {
        given:
        mockECSClient.describeTasks(_) >> Mock(DescribeTasksResult)

        when:
        strategy.discoverNodes()

        then:
        noExceptionThrown()
    }

    def "getDiscoveryNode() - null IP address or port"() {
        when:
        def node = strategy.getDiscoveryNode("127.0.0.1", null)

        then:
        noExceptionThrown()
        node == null

        when:
        node = strategy.getDiscoveryNode(null, 80)

        then:
        noExceptionThrown()
        node == null
    }

    def "getIpAddress() - null container instance"() {
        when:
        def ipAddress = strategy.getIpAddress(null)

        then:
        noExceptionThrown()
        ipAddress == null
    }

    def "getIpAddress() - no ec2 instances doesn't explode"() {
        given:
        strategy = GroovySpy(AmazonECSDiscoveryStrategy,
                constructorArgs: [Mock(ILogger), Mock(Map), Mock(AmazonECS), Mock(AmazonEC2), 0])

        and: "no ec2 instance"
        strategy.getEc2Instance(_) >> null

        when:
        def ipAddress = strategy.getIpAddress(Mock(ContainerInstance))

        then:
        noExceptionThrown()
        ipAddress == null
    }

    def "getTasks() - no tasks returns empty stream"() {
        given:
        ListTasksResult listTasksResult = Mock(ListTasksResult)
        mockECSClient.listTasks(_) >> listTasksResult
        listTasksResult.getTaskArns() >> ["arn"]

        DescribeTasksResult describeTasksResult = Mock(DescribeTasksResult)
        mockECSClient.describeTasks(_) >> describeTasksResult
        describeTasksResult.getTasks() >> []

        when:
        def tasksStream = strategy.getTasks()

        then:
        noExceptionThrown()
        tasksStream.count() == 0
    }

    def "getTaskArns() - non-null service name"() {
        given:
        strategy.serviceName = "someServiceName"

        when:
        strategy.getTaskArns()

        then:
        1 * mockECSClient.listTasks(_ as ListTasksRequest) >> { ListTasksRequest req ->
            assert req.serviceName == "someServiceName"
            return Mock(ListTasksResult)
        }
    }

    def "getEc2Instance() - null container instance"() {
        when:
        def instance = strategy.getEc2Instance(null)

        then:
        noExceptionThrown()
        instance == null
    }

    def "getEc2Instance() - no instances doesn't explode"() {
        given:
        def describeInstancesResult = Mock(DescribeInstancesResult)
        mockEC2Client.describeInstances(_) >> describeInstancesResult
        def reservation = Mock(Reservation)
        describeInstancesResult.getReservations() >> [reservation]
        reservation.getInstances() >> []

        when:
        def instance = strategy.getEc2Instance(Mock(ContainerInstance))

        then:
        noExceptionThrown()
        instance == null
    }

    def "getContainerInstance() - null task"() {
        when:
        def instance = strategy.getContainerInstance(null)

        then:
        noExceptionThrown()
        instance == null
    }

    def "getContainerInstance() - no instances"() {
        given:
        DescribeContainerInstancesResult describeContainerInstancesResult = Mock(DescribeContainerInstancesResult)
        mockECSClient.describeContainerInstances(_) >> describeContainerInstancesResult
        describeContainerInstancesResult.getContainerInstances() >> []

        when:
        def instance = strategy.getContainerInstance(Mock(Task))

        then:
        noExceptionThrown()
        instance == null
    }

}
