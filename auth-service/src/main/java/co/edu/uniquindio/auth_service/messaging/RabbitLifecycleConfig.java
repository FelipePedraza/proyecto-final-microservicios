package co.edu.uniquindio.auth_service.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de auth-service. Mismo patrón del Reto 4: cola durable propia ligada al fanout, con
 * dead-lettering hacia una DLQ por el exchange por defecto. Los exchanges se declaran con los mismos
 * argumentos que el resto de servicios (fanout, durable) para evitar PRECONDITION_FAILED.
 */
@Configuration
public class RabbitLifecycleConfig {

    public static final String EMPLEADOS_EXCHANGE = "empleados_exchange";
    public static final String AUTH_QUEUE = "auth.empleados";
    public static final String AUTH_DLQ = "auth.empleados.dlq";

    /**
     * Conversor JSON (Jackson 3, el equivalente de Jackson2JsonMessageConverter en Spring AMQP 4).
     * Spring Boot lo aplica al RabbitTemplate y a los listeners, de modo que todo lo que publica
     * auth-service con convertAndSend salga como JSON y no como objeto Java serializado.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public FanoutExchange empleadosExchange() {
        return new FanoutExchange(EMPLEADOS_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange authExchange() {
        return new FanoutExchange(AuthEventPublisher.AUTH_EXCHANGE, true, false);
    }

    @Bean
    public Queue authDeadLetterQueue() {
        return QueueBuilder.durable(AUTH_DLQ).build();
    }

    @Bean
    public Queue authQueue() {
        return QueueBuilder.durable(AUTH_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", AUTH_DLQ)
                .build();
    }

    @Bean
    public Binding authQueueBinding(Queue authQueue, FanoutExchange empleadosExchange) {
        return BindingBuilder.bind(authQueue).to(empleadosExchange);
    }
}
