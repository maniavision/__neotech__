package com.neovation.service;

import com.neovation.dto.PaymentConfirmResponse;
import com.neovation.model.Payment;
import com.neovation.model.PaymentStatus;
import com.neovation.model.RequestStatus;
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
import java.util.UUID;

@Service
public class StripePaymentService {
    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    private final ServiceRequestRepository requestRepository;
    private final PaymentRepository paymentRepository;
    private final UserService userService;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public StripePaymentService(ServiceRequestRepository requestRepository, PaymentRepository paymentRepository, UserService userService) {
        this.requestRepository = requestRepository;
        this.paymentRepository = paymentRepository;
        this.userService = userService;
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
     * @return The ID of the created Stripe Checkout Session. <--- MODIFIED RETURN
     * @throws StripeException if the Stripe API call fails.
     * @throws IllegalArgumentException if the request price is missing or zero.
     */
    public String createCheckoutSession(String requestId, Long paymentId, String successUrl) throws StripeException {
        log.info("Creating Stripe Checkout Session for payment ID: {}", paymentId);

        // Fetch the local Payment record to get the exact amount to charge and email
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Local Payment record not found with id: " + paymentId));

        ServiceRequest request = payment.getServiceRequest();

        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Payment failed: Required price is missing or zero for request ID: {}", requestId);
            throw new IllegalArgumentException("Cannot create payment session. Service price is not set.");
        }

        // Convert BigDecimal amount to cents
        long amountInCents = payment.getAmount()
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
//                .setSuccessUrl(successUrl.replace("{REQUEST_ID}", String.valueOf(requestId)))
                .setSuccessUrl(successUrl)
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
        log.info("Stripe Session created successfully for Payment ID: {}. Session ID: {}", paymentId, session.getId());
        return session.getId(); // <--- RETURN THE SESSION ID
    }

    /**
     * Retrieves the URL for a Stripe Checkout Session given its ID.
     * @param sessionId The ID of the Stripe Checkout Session.
     * @return The URL of the Stripe Checkout Session.
     * @throws StripeException if the Stripe API call fails.
     */
    public String generateCheckoutUrl(String sessionId) throws StripeException { // <--- NEW METHOD
        Session session = Session.retrieve(sessionId);
        return session.getUrl();
    }

    /**
     * Retrieves the Stripe session and confirms the associated local payment and request status.
     *
     * @param sessionId The ID of the Stripe Checkout Session.
     * @throws StripeException if communication with Stripe fails.
     * @throws IllegalStateException if the payment is already processed or the session status is invalid.
     */
    public PaymentConfirmResponse confirmSession(String sessionId) throws StripeException { // <-- MODIFIED RETURN TYPE
        log.info("Starting confirmation process for Stripe session: {}", sessionId);

        PaymentConfirmResponse response = new PaymentConfirmResponse(false, "Unknown Error");

        // 1. Retrieve the session from Stripe
        Session session = Session.retrieve(sessionId);

        // 2. Validate Stripe session status
        if (!"complete".equals(session.getStatus())) {
            String msg = String.format("Stripe payment status is %s. Cannot confirm payment.", session.getPaymentStatus());
            log.error("Stripe Session {} payment status is not 'complete': {}", sessionId, session.getPaymentStatus());
            response.setMessage(msg);
            return response;
        }

        // 3. Extract metadata for local lookup
        Map<String, String> metadata = session.getMetadata();
        String paymentIdStr = metadata.get("paymentId");
        final String serviceRequestId = metadata.get("serviceRequestId");

        if (paymentIdStr == null || paymentIdStr.isBlank()) {
            String msg = "Stripe metadata missing or invalid paymentId. Cannot complete confirmation.";
            log.warn(msg);
            response.setMessage(msg);
            return response;
        }

        final Long finalPaymentId = Long.valueOf(paymentIdStr);

        // 4. Update the local Payment record
        Payment payment = paymentRepository.findById(finalPaymentId)
                .orElseThrow(() -> new EntityNotFoundException("Local Payment record not found with id: " + finalPaymentId));

        // Populate response with known info
        response.setPaymentId(paymentIdStr);
        response.setRequestId(serviceRequestId);
        response.setAmount(payment.getAmount());
        response.setCurrency(session.getCurrency() != null ? session.getCurrency().toUpperCase() : "USD");

        // Security/Idempotency check: prevent double processing
        if (payment.getPaymentStatus().equals(PaymentStatus.COMPLETED)) {
            String msg = "Payment ID " + finalPaymentId + " already processed.";
            log.warn(msg);
            // Return success if already processed (idempotency)
            response.setSuccess(true);
            response.setMessage("Payment already confirmed.");
            response.setStatus(payment.getPaymentStatus().name());
            return response;
        }

        // Perform updates
        payment.setPaymentStatus(PaymentStatus.COMPLETED);
        payment.setSessionId(sessionId);
        paymentRepository.save(payment);
        log.info("Local Payment ID {} status updated to COMPLETED.", finalPaymentId);

        // 5. Update the ServiceRequest status
        ServiceRequest serviceRequest = requestRepository.findById(serviceRequestId)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + serviceRequestId));

        serviceRequest.setStatus(RequestStatus.PAYMENT_RECEIVED);
        requestRepository.save(serviceRequest);
        log.info("ServiceRequest ID {} status updated to PAYMENT_RECEIVED.", serviceRequestId);

        // 6. Send receipt email
        userService.sendPaymentReceiptEmail(payment);
        log.info("Sent payment receipt email for Payment ID: {}", finalPaymentId);

        // 7. Prepare and return success response
        response.setSuccess(true);
        response.setMessage("Payment confirmed and status updated.");
        response.setStatus(payment.getPaymentStatus().name()); // COMPLETED
        return response;
    }
}