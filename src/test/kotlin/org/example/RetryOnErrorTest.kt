package org.example

import io.micronaut.configuration.kafka.annotation.ErrorStrategy
import io.micronaut.configuration.kafka.annotation.ErrorStrategyValue
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetStrategy
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import kotlin.test.assertEquals

const val topic = "test"

const val failOnKey = 15

@KafkaClient
interface EventProducer {
    @Topic(topic)
    fun send(@KafkaKey key: Int, value: Int)
}

@KafkaListener(
    offsetStrategy = OffsetStrategy.SYNC_PER_RECORD,
    errorStrategy = ErrorStrategy(
        ErrorStrategyValue.RETRY_ON_ERROR,
        retryCount = 1,
    ),
    // Uncommenting the following lines will fix the test
//    properties = [
//        Property(name = ConsumerConfig.MAX_POLL_RECORDS_CONFIG, value = "1"),
//    ],
)
class EventListener {
    val receivedKeys = mutableSetOf<Int>()

    @Topic(topic)
    fun receive(consumerRecord: ConsumerRecord<Int, Int>) {
        println("Received record with partition: ${consumerRecord.partition()}, offset: ${consumerRecord.offset()}, key: ${consumerRecord.key()}")

        // Optional delay in processing, not required in my environment
        // Thread.sleep(100)

        if (consumerRecord.key() == failOnKey)
            throw RuntimeException("Failed on $failOnKey")

        receivedKeys.add(consumerRecord.key())
    }
}

@Testcontainers
@MicronautTest
@TestInstance(Lifecycle.PER_CLASS)
class RetryOnErrorTest : TestPropertyProvider {
    @Inject
    lateinit var eventProducer: EventProducer

    @Inject
    lateinit var eventListener: EventListener

    @Test
    fun `send and process all messages except one`() {
        val sentKeys = mutableSetOf<Int>()
        for (key in 0 until 100) {
            eventProducer.send(key, key)
            sentKeys.add(key)
        }

        // For me, this might fail on (only 15 is expected):
        // 11, 12, 14, 15, 16
        // 11, 12, 14, 15, 16, 18
        // 12, 14, 15, 16, 18, 19, 22
        // 15, 16, 18, 19
        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            val missingKeys = sentKeys - eventListener.receivedKeys
            assertEquals(setOf(failOnKey), missingKeys)
        }
    }

    override fun getProperties(): MutableMap<String, String> {
        // We need to create topic here, before the Consumer subscribes to it
        val adminClientProperties = Properties()
        adminClientProperties[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafka.bootstrapServers
        val admin = Admin.create(adminClientProperties)
        admin.createTopics(
            listOf(
                NewTopic(topic, 5, 1),
            )
        )

        return mutableMapOf(
            "kafka.bootstrap.servers" to kafka.bootstrapServers,
        )
    }

    companion object {
        @Container
        val kafka: KafkaContainer =
            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.2.1"))
                .withEmbeddedZookeeper()
    }
}
