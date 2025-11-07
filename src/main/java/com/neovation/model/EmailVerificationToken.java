package com.neovation.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verification_token")
public class EmailVerificationToken extends AbstractToken {}
