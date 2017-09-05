# hazelcast-discovery-amazon-ecs

A [Hazelcast member discovery strategy](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#discovery-spi)
for [Amazon ECS](https://aws.amazon.com/ecs/).

# Usage

Configure Hazelcast to use the `AmazonECSDiscoveryStrategy`:

```java
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.commercehub.hazelcast.spi.discovery.AmazonECSDiscoveryException;
import com.commercehub.hazelcast.spi.discovery.AmazonECSDiscoveryStrategyFactory;
import com.commercehub.hazelcast.spi.discovery.AmazonECSDiscoveryUtils;
import com.hazelcast.config.Config;
import com.hazelcast.config.DiscoveryStrategyConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.properties.GroupProperty;

Config hazelcastConfig = new Config();
hazelcastConfig.setProperty(GroupProperty.DISCOVERY_SPI_ENABLED.getName(), String.valueOf(true));

NetworkConfig hazelcastNetworkConfig = hazelcastConfig.getNetworkConfig();

AmazonECS amazonECS = AmazonECSClientBuilder.defaultClient();
AmazonECSDiscoveryUtils amazonECSDiscoveryUtils = new AmazonECSDiscoveryUtils(amazonECS);
try {
    hazelcastNetworkConfig.setPublicAddress(
            amazonECSDiscoveryUtils.discoverPublicHazelcastAddress(hazelcastNetworkConfig.getPort()));
} catch (AmazonECSDiscoveryException e) {
    log.error("Failed to set public address on Hazelcast network config", e);
}

JoinConfig hazelcastJoinConfig = hazelcastNetworkConfig.getJoin();
hazelcastJoinConfig.getMulticastConfig().setEnabled(false);
hazelcastJoinConfig.getTcpIpConfig().setEnabled(false);
hazelcastJoinConfig.getAwsConfig().setEnabled(false);
hazelcastJoinConfig.getDiscoveryConfig().addDiscoveryStrategyConfig(
        new DiscoveryStrategyConfig(new AmazonECSDiscoveryStrategyFactory(
                amazonECS,
                AmazonEC2ClientBuilder.defaultClient(),
                hazelcastNetworkConfig.getPort())));

HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastConfig);
```
