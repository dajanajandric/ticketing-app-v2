package pozoriste1.ticketing.saga;

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
 * ticketing-service strana sage brisanja gledaoca (koreografija, vidi i
 * RabbitConfig u users-service). Slusa zahtjev za brisanje, a odgovara
 * dogadjajem approved ili rejected.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "pozoriste.events";

    public static final String DELETION_REQUESTED = "spectator.deletion.requested";
    public static final String DELETION_APPROVED = "spectator.deletion.approved";
    public static final String DELETION_REJECTED = "spectator.deletion.rejected";

    public static final String REQUESTED_QUEUE = "ticketing.spectator-deletion-requested";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // Trajni red: ako ticketing-service ne radi, zahtjev ceka i saga se nastavlja kad se servis vrati
    @Bean
    public Queue deletionRequestedQueue() {
        return QueueBuilder.durable(REQUESTED_QUEUE).build();
    }

    @Bean
    public Binding deletionRequestedBinding() {
        return BindingBuilder.bind(deletionRequestedQueue()).to(eventsExchange()).with(DELETION_REQUESTED);
    }

    // Tip poruke se odredjuje iz parametra listener-a (vidi users-service RabbitConfig)
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
