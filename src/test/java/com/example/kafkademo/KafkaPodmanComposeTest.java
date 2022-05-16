package com.example.kafkademo;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import com.palantir.docker.compose.DockerComposeExtension;
import com.palantir.docker.compose.connection.waiting.HealthChecks;

@RunWith(SpringRunner.class)
@Import(com.example.kafkademo.KafkaPodmanComposeTest.KafkaTestContainersConfiguration.class)
@SpringBootTest(classes = KafkaProducerConsumerApplication.class)
@DirtiesContext
public class KafkaPodmanComposeTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPodmanComposeTest.class);

	// @Container
	// public static KafkaContainer kafka = new
	// KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

	private static final int KAFKA_PORT = 29092;

	private static String KAFKA_SERVICE = "kafka";
	
	  
	/*
	 * @Container private static final DockerComposeContainer kafka = new
	 * DockerComposeContainer( new
	 * File("src/test/resources/compose-test.yml")).withOptions("--compatibility")
	 * .withExposedService(KAFKA_SERVICE, KAFKA_PORT,
	 * Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(360)))
	 * .withLocalCompose(true);
	 */
	 

	@ClassRule
	private static final DockerComposeExtension kafka = DockerComposeExtension.builder()
		    .file("src/test/resources/compose-test.yml")
		    .waitingForService(KAFKA_SERVICE, HealthChecks.toHaveAllPortsOpen())
		    .build();
	
	@Autowired
	public KafkaTemplate<String, String> template;

	@Autowired
	private KafkaConsumer consumer;

	@Autowired
	private KafkaProducer producer;

	@Value("${test.topic}")
	private String topic;

	@Test
	public void givenKafkaDockerContainer_whenSendingtoDefaultTemplate_thenMessageReceived() throws Exception {
		template.send(topic, "Sending with default template");
		consumer.getLatch().await(10000, TimeUnit.MILLISECONDS);

		assertThat(consumer.getLatch().getCount(), equalTo(0L));
		assertThat(consumer.getPayload(), containsString("embedded-test-topic"));
	}

	@Test
	public void givenKafkaDockerContainer_whenSendingtoSimpleProducer_thenMessageReceived() throws Exception {
		producer.send(topic, "Sending with own controller");
		consumer.getLatch().await(10000, TimeUnit.MILLISECONDS);

		assertThat(consumer.getLatch().getCount(), equalTo(0L));
		assertThat(consumer.getPayload(), containsString("embedded-test-topic"));
	}

	@TestConfiguration
	static class KafkaTestContainersConfiguration {

		@Bean
		ConcurrentKafkaListenerContainerFactory<Integer, String> kafkaListenerContainerFactory() {
			ConcurrentKafkaListenerContainerFactory<Integer, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
			factory.setConsumerFactory(consumerFactory());
			return factory;
		}

		@Bean
		public ConsumerFactory<Integer, String> consumerFactory() {
			return new DefaultKafkaConsumerFactory<>(consumerConfigs());
		}

		@Bean
		public Map<String, Object> consumerConfigs() {
			Map<String, Object> props = new HashMap<>();
			// props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
			// kafka.getServiceHost(KAFKA_SERVICE,
			// KAFKA_PORT)+":"+kafka.getServicePort(KAFKA_SERVICE, KAFKA_PORT));
			props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost" + ":" + "29092");
			props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
			props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafkademogroup");
			props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
			props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
			return props;
		}

		@Bean
		public ProducerFactory<String, String> producerFactory() {
			Map<String, Object> configProps = new HashMap<>();
			// configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
			// kafka.getServiceHost(KAFKA_SERVICE,
			// KAFKA_PORT)+":"+kafka.getServicePort(KAFKA_SERVICE, KAFKA_PORT));
			configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost" + ":" + "29092");
			configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			return new DefaultKafkaProducerFactory<>(configProps);
		}

		@Bean
		public KafkaTemplate<String, String> kafkaTemplate() {
			return new KafkaTemplate<>(producerFactory());
		}

	}

}
