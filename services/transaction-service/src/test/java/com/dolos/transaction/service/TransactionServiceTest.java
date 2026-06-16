package com.dolos.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dolos.transaction.api.dto.CreateTransactionRequest;
import com.dolos.transaction.api.dto.TransactionResponse;
import com.dolos.transaction.domain.Direction;
import com.dolos.transaction.domain.TransactionEntity;
import com.dolos.transaction.repo.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository repository;

    @InjectMocks private TransactionService service;

    @Test
    void create_mapsRequestToEntityAndReturnsResponse() {
        var request =
                new CreateTransactionRequest(
                        "ACC-1",
                        "ACC-2",
                        new BigDecimal("100.0000"),
                        "usd",
                        Direction.DEBIT,
                        "coffee",
                        Instant.parse("2026-01-01T00:00:00Z"));
        // Repository echoes back whatever it is asked to save.
        when(repository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = service.create(request);

        assertThat(response.account().value()).isEqualTo("ACC-1");
        assertThat(response.counterparty().value()).isEqualTo("ACC-2");
        assertThat(response.amount().amount()).isEqualByComparingTo("100.0000");
        assertThat(response.amount().currency().getCurrencyCode()).isEqualTo("USD"); // normalised
        assertThat(response.direction()).isEqualTo(Direction.DEBIT);
        assertThat(response.id()).isNotNull();
        verify(repository).save(any(TransactionEntity.class));
    }

    @Test
    void getById_whenMissing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
