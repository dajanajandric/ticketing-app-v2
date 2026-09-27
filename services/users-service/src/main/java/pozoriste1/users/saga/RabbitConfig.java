package pozoriste1.users.saga;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Saga brisanja gledaoca (koreografija): servisi ne zovu jedan drugog, nego
 * objavljuju dogadjaje na zajednicki exchange i reaguju na tudje dogadjaje.
 *
 *   users-service     --spectator.deletion.requested-->  ticketing-service
 *   ticketing-service --spectator.deletion.approved--->  users-service (brise gledaoca)
 *   ticketing-service --spectator.deletion.rejected--->  users-service (kompenzacija: vraca ACTIVE)
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "pozoriste.events";

    public static final String DELETION_REQUESTED = "spectator.deletion.requested";
    public static final String DELETION_APPROVED = "spectator.deletion.approved";
    public static final String DELETION_REJECTED = "spectator.deletion.rejected";

    public static final String APPROVED_QUEUE = "users.spectator-deletion-approved";
    public static final String REJECTED_QUEUE = "users.spectator-deletion-rejected";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // Trajni redovi: ako users-service ne radi, odgovor ticketing-a ceka u redu
    @Bean
    public Queue deletionApprovedQueue() {
        return QueueBuilder.durable(APPROVED_QUEUE).build();
    }

    @Bean
    public Queue deletionRejectedQueue() {
        return QueueBuilder.durable(REJECTED_QUEUE).build();
    }

    @Bean
    public Binding deletionApprovedBinding() {
        return BindingBuilder.bind(deletionApprovedQueue()).to(eventsExchange()).with(DELETION_APPROVED);
    }

    @Bean
    public Binding deletionRejectedBinding() {
        return BindingBuilder.bind(deletionRejectedQueue()).to(eventsExchange()).with(DELETION_REJECTED);
    }

    // Poruke su JSON; tip se odredjuje iz parametra listener-a, ne iz imena Java klase
    // posiljaoca (servisi imaju svoje kopije klasa dogadjaja u razlicitim paketima).
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
