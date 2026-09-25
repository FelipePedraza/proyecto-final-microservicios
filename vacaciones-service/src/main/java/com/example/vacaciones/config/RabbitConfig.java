package com.example.vacaciones.config;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    private static final String EMPLOYEES_EXCHANGE = "empleados_exchange";
    private static final String EMPLOYEES_QUEUE = "vacaciones.empleados";
    private static final String EMPLOYEES_DLQ = "vacaciones.empleados.dlq";

    @Bean
    public FanoutExchange employeesExchange() {
        return new FanoutExchange(EMPLOYEES_EXCHANGE, true, false);
    }

    @Bean
    public Queue employeeQueue() {
        return QueueBuilder.durable(EMPLOYEES_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", EMPLOYEES_DLQ)
                .build();
    }

    @Bean
    public Binding employeeBinding() {
        return BindingBuilder.bind(employeeQueue()).to(employeesExchange());
    }

    @Bean
    public Queue employeeDlq() {
        return QueueBuilder.durable(EMPLOYEES_DLQ).build();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new JacksonJsonMessageConverter());
        return template;
    }

    @Bean
    public MessageConverter employeeEventMessageConverter() {
        return new SimpleMessageConverter() {
            @Override
            public Object fromMessage(Message message) {
                return new String(message.getBody(), StandardCharsets.UTF_8);
            }
        };
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("employeeEventMessageConverter") MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
