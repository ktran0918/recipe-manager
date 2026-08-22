package com.recipemanager.api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring AMQP auto-detects @Bean declarations of Queue, Exchange, and Binding
// and uses RabbitAdmin to create them on the broker at startup.
// C# equivalent: registering an IHostedService that calls channel.QueueDeclare on startup.
@Configuration
public class RabbitMqConfig {

    public static final String QUEUE_NAME = "scrape.jobs";
    public static final String DLX_NAME   = "scrape.jobs.dlx";
    public static final String DLQ_NAME   = "scrape.jobs.dead";

    @Bean
    public Queue scrapeJobsQueue() {
        // QueueBuilder.durable() survives broker restarts (like a persistent queue in RabbitMQ).
        // x-dead-letter-exchange: messages rejected after MAX_RETRIES land here instead of vanishing.
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    // Binding wires the DLQ to the DLX with the original queue name as the routing key.
    // Spring resolves the Queue and DirectExchange args by bean type — no name lookup needed.
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(QUEUE_NAME);
    }

    // By default RabbitTemplate uses Java serialisation — opaque bytes to the Python consumer.
    // Jackson2JsonMessageConverter switches serialisation to JSON.
    // C# equivalent: configuring a custom IMessageSerializer in MassTransit/RawRabbit.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}