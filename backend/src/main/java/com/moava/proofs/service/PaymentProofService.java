package com.moava.proofs.service;

import com.moava.proofs.mapper.PaymentProofMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor   // final 필드를 생성자로 주입
public class PaymentProofService {
    private final PaymentProofMapper paymentProofMapper;
}