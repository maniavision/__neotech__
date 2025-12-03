package com.neovation.controller;

import com.neovation.dto.PaymentConfirmResponse;
import com.neovation.service.StripePaymentService;
import com.stripe.exception.StripeException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final StripePaymentService stripePaymentService;

    public PaymentController(StripePaymentService stripePaymentService) {
        this.stripePaymentService = stripePaymentService;
    }

    /**
     * GET /api/payments/confirm-session
     * This endpoint is the success_url redirect target from Stripe.
     * It relies on the 'session_id' query parameter provided automatically by Stripe.
     */
    @GetMapping("/confirm-session")
    public ResponseEntity<?> confirmPaymentSession(@RequestParam(name = "session_id") String sessionId) {
        log.info("Received request to confirm Stripe session ID: {}", sessionId);
        try {
            PaymentConfirmResponse confirmResponse = stripePaymentService.confirmSession(sessionId);
            return ResponseEntity.ok(confirmResponse);
        } catch (EntityNotFoundException e) {
            log.error("Payment confirmation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe API error during payment confirmation for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("success", false, "message", "Stripe error: " + e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Payment status error for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}