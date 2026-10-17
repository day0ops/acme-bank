package io.acmebank.transactions.rest;

import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.persistence.TransactionRepository;
import io.acmebank.transactions.saga.TransferSaga;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST endpoint for transactions; delegates transfers to {@link TransferSaga}, the gRPC layer's sibling. */
@RestController
@RequestMapping("/transactions")
class TransactionController {

    private final TransferSaga saga;
    private final TransactionRepository repository;

    TransactionController(TransferSaga saga, TransactionRepository repository) {
        this.saga = saga;
        this.repository = repository;
    }

    record TransferBody(String fromAccountId, String toAccountId, String currencyCode, long amountMinorUnits) {}
    record TransactionResponse(String id, String fromAccountId, String toAccountId, String currencyCode,
                                long amountMinorUnits, String status, String failureReason) {
        static TransactionResponse from(Transaction tx) {
            return new TransactionResponse(tx.id(), tx.fromAccountId(), tx.toAccountId(), tx.currencyCode(),
                    tx.amountMinorUnits(), tx.status().toString(), tx.failureReason());
        }
    }

    @PostMapping
    ResponseEntity<TransactionResponse> transfer(@RequestBody TransferBody body) {
        Transaction result = saga.execute(body.fromAccountId(), body.toAccountId(), body.currencyCode(), body.amountMinorUnits());
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(result));
    }

    @GetMapping("/{id}")
    TransactionResponse get(@PathVariable String id) {
        return repository.findById(id).map(TransactionResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @GetMapping
    List<TransactionResponse> listByAccount(@RequestParam String accountId) {
        return repository.findByAccountId(accountId).stream().map(TransactionResponse::from).toList();
    }
}
