package io.acmebank.fraudrisk.rest;

import io.acmebank.fraudrisk.domain.FlaggedTransaction;
import io.acmebank.fraudrisk.persistence.FlaggedTransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/flagged-transactions")
class FlaggedTransactionController {

    private final FlaggedTransactionRepository repository;

    FlaggedTransactionController(FlaggedTransactionRepository repository) {
        this.repository = repository;
    }

    record FlaggedTransactionResponse(String id, String transactionId, String reason, String currencyCode, long amountMinorUnits) {
        static FlaggedTransactionResponse from(FlaggedTransaction f) {
            return new FlaggedTransactionResponse(f.id(), f.transactionId(), f.reason(), f.currencyCode(), f.amountMinorUnits());
        }
    }

    @GetMapping
    List<FlaggedTransactionResponse> list() {
        return repository.findAll().stream().map(FlaggedTransactionResponse::from).toList();
    }
}
