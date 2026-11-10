package com.moava.proofs.controller;

import com.moava.proofs.service.PaymentProofService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentProofController {
    private final PaymentProofService paymentProofService;
}