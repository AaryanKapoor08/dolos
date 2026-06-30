package com.dolos.transaction.api;

import com.dolos.transaction.api.dto.CreateTransactionRequest;
import com.dolos.transaction.api.dto.TransactionResponse;
import com.dolos.transaction.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for transactions. */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/transactions/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    /**
     * Recent transactions for an account, newest first. Backs the copilot's
     * {@code getTransactionHistory} tool (Phase 4D); {@code limit} is clamped to [1, 200] so a tool
     * call can't request an unbounded scan.
     */
    @GetMapping(params = "accountId")
    public List<TransactionResponse> listByAccount(
            @RequestParam String accountId,
            @RequestParam(name = "limit", defaultValue = "25") int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return service.findByAccount(accountId, capped);
    }
}
