package com.aub.mobilebanking.uk;

import com.google.android.gms.tapandpay.issuer.GeneratePaymentCredentialsRequest;
import com.google.android.gms.tapandpay.issuer.GeneratePaymentCredentialsResponse;

import java.util.concurrent.CompletableFuture;

public class PendingProvisionRequest {
    public final CompletableFuture<GeneratePaymentCredentialsResponse> future;
    public final GeneratePaymentCredentialsRequest request;

    public PendingProvisionRequest(
            CompletableFuture<GeneratePaymentCredentialsResponse> future,
            GeneratePaymentCredentialsRequest request) {

        this.future = future;
        this.request = request;
    }
}
