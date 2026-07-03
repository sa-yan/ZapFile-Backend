package com.sayan.zapfile.transfer;

import com.sayan.zapfile.transfer.TransferDtos.AcceptTransferRequest;
import com.sayan.zapfile.transfer.TransferDtos.BatchResponse;
import com.sayan.zapfile.transfer.TransferDtos.CreateTransferRequest;
import com.sayan.zapfile.transfer.TransferDtos.TransferResponse;
import com.sayan.zapfile.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatchResponse create(@AuthenticationPrincipal User user,
                                @Valid @RequestBody CreateTransferRequest request) {
        return transferService.createOffer(user, request);
    }

    @PostMapping("/batch/{batchId}/accept")
    public BatchResponse acceptBatch(@AuthenticationPrincipal User user,
                                     @PathVariable String batchId,
                                     @Valid @RequestBody AcceptTransferRequest request) {
        return transferService.acceptBatch(user, batchId, request.receiverDeviceId());
    }

    @PostMapping("/batch/{batchId}/decline")
    public BatchResponse declineBatch(@AuthenticationPrincipal User user, @PathVariable String batchId) {
        return transferService.declineBatch(user, batchId);
    }

    @PostMapping("/batch/{batchId}/cancel")
    public BatchResponse cancelBatch(@AuthenticationPrincipal User user, @PathVariable String batchId) {
        return transferService.cancelBatch(user, batchId);
    }

    @GetMapping
    public List<TransferResponse> history(@AuthenticationPrincipal User user) {
        return transferService.history(user);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@AuthenticationPrincipal User user, @PathVariable String id) {
        return transferService.get(user, id);
    }

    @PostMapping("/{id}/accept")
    public TransferResponse accept(@AuthenticationPrincipal User user,
                                   @PathVariable String id,
                                   @Valid @RequestBody AcceptTransferRequest request) {
        return transferService.accept(user, id, request.receiverDeviceId());
    }

    @PostMapping("/{id}/decline")
    public TransferResponse decline(@AuthenticationPrincipal User user, @PathVariable String id) {
        return transferService.decline(user, id);
    }

    @PostMapping("/{id}/cancel")
    public TransferResponse cancel(@AuthenticationPrincipal User user, @PathVariable String id) {
        return transferService.cancel(user, id);
    }

    @PostMapping("/{id}/complete")
    public TransferResponse complete(@AuthenticationPrincipal User user, @PathVariable String id) {
        return transferService.complete(user, id);
    }

    @PostMapping("/{id}/fail")
    public TransferResponse fail(@AuthenticationPrincipal User user, @PathVariable String id) {
        return transferService.fail(user, id);
    }

    @PostMapping("/{id}/resume")
    public TransferResponse resume(@AuthenticationPrincipal User user, @PathVariable String id) {
        return transferService.resume(user, id);
    }
}
