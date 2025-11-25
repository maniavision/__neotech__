package com.neovation.dto;

public class StripeCheckoutResponse {
    private String checkoutUrl;

    public StripeCheckoutResponse(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }
}