package com.template.springboottemplate.controller;

import com.template.springboottemplate.model.Country;
import com.template.springboottemplate.repository.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private static final Logger log = LoggerFactory.getLogger(CountryController.class);
    private final CountryRepository countryRepository;

    public CountryController(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @GetMapping
    public ResponseEntity<List<Country>> getAllCountries() {
        log.info("Fetching all countries");
        // You might want to sort these by name for easier frontend display
        // e.g., countryRepository.findAll(Sort.by("name"))
        return ResponseEntity.ok(countryRepository.findAll());
    }
}