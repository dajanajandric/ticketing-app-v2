package pozoriste1.users.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import pozoriste1.users.saga.SpectatorDeletionEvents.Approved;
import pozoriste1.users.saga.SpectatorDeletionEvents.Rejected;
import pozoriste1.users.saga.SpectatorDeletionEvents.Requested;
import pozoriste1.users.spectators.Spectator;
import pozoriste1.users.spectators.SpectatorRepository;
import pozoriste1.users.spectators.SpectatorStatus;

@ExtendWith(MockitoExtension.class)
class SpectatorDeletionSagaTest {

    private static final String JMBG = "0101990800007";

    @Mock
    private SpectatorRepository repository;

    @Mock
    private RabbitTemplate rabbit;

    @InjectMocks
    private SpectatorDeletionSaga saga;

    private static Spectator spectator(SpectatorStatus status) {
        Spectator s = new Spectator();
        s.setJmbg(JMBG);
        s.setFirstName("Ana");
        s.setLastName("Petrović");
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("Pokretanje sage: gledalac prelazi u DELETION_PENDING i objavljuje se deletion.requested")
    void startMarksSpectatorAndPublishesEvent() {
        Spectator s = spectator(SpectatorStatus.ACTIVE);
        when(repository.findById(JMBG)).thenReturn(Optional.of(s));

        String sagaId = saga.start(JMBG);

        assertThat(s.getStatus()).isEqualTo(SpectatorStatus.DELETION_PENDING);
        verify(repository).save(s);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.DELETION_REQUESTED), event.capture());
        assertThat(event.getValue()).isEqualTo(new Requested(sagaId, JMBG));
    }

    @Test
    @DisplayName("Drugo brisanje dok prvo traje se odbija i ne objavljuje nista")
    void startRejectsWhenAlreadyPending() {
        when(repository.findById(JMBG)).thenReturn(Optional.of(spectator(SpectatorStatus.DELETION_PENDING)));

        assertThatThrownBy(() -> saga.start(JMBG)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(rabbit);
    }

    @Test
    @DisplayName("Brisanje nepostojeceg gledaoca se odbija")
    void startRejectsUnknownSpectator() {
        when(repository.findById(JMBG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saga.start(JMBG)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rabbit);
    }

    @Test
    @DisplayName("Ako broker ne radi, izuzetak se propagira (transakcija se ponistava, gledalac ostaje ACTIVE)")
    void startPropagatesBrokerFailure() {
        when(repository.findById(JMBG)).thenReturn(Optional.of(spectator(SpectatorStatus.ACTIVE)));
        doThrow(new AmqpException("broker ne radi"))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatThrownBy(() -> saga.start(JMBG)).isInstanceOf(AmqpException.class);
    }

    @Test
    @DisplayName("approved: gledalac u DELETION_PENDING se brise")
    void approvedDeletesPendingSpectator() {
        Spectator s = spectator(SpectatorStatus.DELETION_PENDING);
        when(repository.findById(JMBG)).thenReturn(Optional.of(s));

        saga.onApproved(new Approved("saga-1", JMBG, 2));

        verify(repository).delete(s);
    }

    @Test
    @DisplayName("approved za gledaoca koji nije u DELETION_PENDING se ignorise (ponovljena poruka)")
    void approvedIgnoresActiveSpectator() {
        when(repository.findById(JMBG)).thenReturn(Optional.of(spectator(SpectatorStatus.ACTIVE)));

        saga.onApproved(new Approved("saga-1", JMBG, 0));

        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("rejected: kompenzacija vraca gledaoca u ACTIVE")
    void rejectedRestoresActiveStatus() {
        Spectator s = spectator(SpectatorStatus.DELETION_PENDING);
        when(repository.findById(JMBG)).thenReturn(Optional.of(s));

        saga.onRejected(new Rejected("saga-1", JMBG, 3, "ima buducih karata"));

        assertThat(s.getStatus()).isEqualTo(SpectatorStatus.ACTIVE);
        verify(repository).save(s);
        verify(repository, never()).delete(any());
    }
}
