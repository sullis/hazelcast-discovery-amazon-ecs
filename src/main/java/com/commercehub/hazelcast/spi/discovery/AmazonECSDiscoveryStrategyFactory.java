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

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ecs.AmazonECS;
import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("unused")
public class AmazonECSDiscoveryStrategyFactory implements DiscoveryStrategyFactory {

    private final AmazonECS ecsClient;
    private final AmazonEC2 ec2Client;
    private final int containerPort;
    
    public AmazonECSDiscoveryStrategyFactory(AmazonECS ecsClient, AmazonEC2 ec2Client, int containerPort) {
        this.ecsClient = ecsClient;
        this.ec2Client = ec2Client;
        this.containerPort = containerPort;
    }
    
    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return AmazonECSDiscoveryStrategy.class;
    }

    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        return Collections.unmodifiableCollection(Collections.emptyList());
    }

    @Override
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode,
                                                  ILogger logger,
                                                  Map<String, Comparable> properties) {
        
        return new AmazonECSDiscoveryStrategy(logger, properties, ecsClient, ec2Client, containerPort);
    }
    
}
