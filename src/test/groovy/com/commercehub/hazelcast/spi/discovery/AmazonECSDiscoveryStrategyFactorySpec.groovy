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

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ecs.AmazonECSClient
import com.hazelcast.logging.ILogger
import com.hazelcast.spi.discovery.DiscoveryStrategy
import spock.lang.Specification

class AmazonECSDiscoveryStrategyFactorySpec extends Specification {

    AmazonECSDiscoveryStrategyFactory factory

    def mockECSClient = Mock(AmazonECSClient)
    def mockEC2Client = Mock(AmazonEC2Client)
    def containerPort = 12345

    def setup() {
        factory = new AmazonECSDiscoveryStrategyFactory(mockECSClient, mockEC2Client, containerPort)
    }

    def "getDiscoveryStrategyType returns correct type"() {
        expect:
        factory.getDiscoveryStrategyType() == AmazonECSDiscoveryStrategy
    }

    def "getConfigurationProperties() returns empty properties"() {
        expect:
        factory.getConfigurationProperties().isEmpty()
    }

    def "newDiscoveryStrategy() - happy path"() {
        when:
        DiscoveryStrategy strategy = factory.newDiscoveryStrategy(null, Mock(ILogger), Collections.emptyMap())

        then:
        strategy instanceof AmazonECSDiscoveryStrategy

        then:
        ((AmazonECSDiscoveryStrategy)strategy).ecsClient == mockECSClient
        ((AmazonECSDiscoveryStrategy)strategy).ec2Client == mockEC2Client
        ((AmazonECSDiscoveryStrategy)strategy).containerPort == containerPort

    }

}
