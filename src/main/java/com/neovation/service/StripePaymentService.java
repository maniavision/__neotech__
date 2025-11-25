package com.neovation.service;

import com.neovation.model.Payment;
import com.neovation.model.ServiceRequest;
import com.neovation.repository.PaymentRepository;
import com.neovation.repository.ServiceRequestRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
public class StripePaymentService {
    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    private final ServiceRequestRepository requestRepository;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public StripePaymentService(ServiceRequestRepository requestRepository, PaymentRepository paymentRepository) {
        this.requestRepository = requestRepository;
        this.paymentRepository = paymentRepository;
    }

    @PostConstruct
    public void init() {
        // Set the Stripe API key programmatically
        Stripe.apiKey = secretKey;
        log.info("Stripe API initialized.");
    }

    /**
     * Creates a Stripe Checkout Session for a given ServiceRequest.
     *
     * @param requestId The ID of the ServiceRequest.
     * @param paymentId The ID of the local Payment record to link (used for metadata).
     * @return The URL of the Stripe Checkout Session.
     * @throws StripeException if the Stripe API call fails.
     * @throws IllegalArgumentException if the request price is missing or zero.
     */
    public String createCheckoutSession(Long requestId, Long paymentId) throws StripeException {
        log.info("Creating Stripe Checkout Session for payment ID: {}", paymentId);

        // Fetch the local Payment record to get the exact amount to charge and email
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment record not found with id: " + paymentId));

        ServiceRequest request = payment.getServiceRequest();

        // Defense-in-depth: validation should mostly happen in RequestService, but we ensure the price is set.
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Payment failed: Required price is missing or zero for request ID: {}", requestId);
            throw new IllegalArgumentException("Cannot create payment session. Service price is not set.");
        }

        // Convert BigDecimal amount to cents
        long amountInCents = payment.getAmount() // <--- USE AMOUNT FROM PAYMENT RECORD
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .longValue();

        // Prepare metadata to link Stripe session back to internal records
        Map<String, String> metadata = new HashMap<>();
        metadata.put("serviceRequestId", String.valueOf(requestId));
        metadata.put("paymentId", String.valueOf(paymentId));
        metadata.put("customerEmail", payment.getEmail());

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                // Use email from the Payment record for pre-filling the Stripe form
                .setCustomerEmail(payment.getEmail())
                // Replace placeholders in application.properties URLs
                .setSuccessUrl(successUrl.replace("{REQUEST_ID}", String.valueOf(requestId)))
                .setCancelUrl(cancelUrl.replace("{REQUEST_ID}", String.valueOf(requestId)))
                .putAllMetadata(metadata)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(request.getTitle())
                                                                .setDescription("Service Request ID: " + requestId)
                                                                .build())
                                                .build())
                                .build())
                .build();

        Session session = Session.create(params);
        log.info("Stripe Session created successfully for Payment ID: {}. URL: {}", paymentId, session.getUrl());
        return session.getUrl();
    }
}