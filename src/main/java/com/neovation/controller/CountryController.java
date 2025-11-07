package com.neovation.controller;

import com.neovation.service.CountryService;
import com.neovation.model.Country;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private static final Logger log = LoggerFactory.getLogger(CountryController.class);
    private final CountryService countryService;

    public CountryController(CountryService countryService) {
        this.countryService = countryService;
    }

    @GetMapping
    public ResponseEntity<List<Country>> getAllCountries() {
        log.info("Fetching all countries");
        // You might want to sort these by name for easier frontend display
        // e.g., countryRepository.findAll(Sort.by("name"))
        return ResponseEntity.ok(countryService.getAllCountries());
    }
    @GetMapping("/suggested-countries")
    public List<Country> getCountrySuggestions(@RequestParam(name = "query", required = false, defaultValue = "") String query) {
        // Basic validation: Don't query if input is too short
        if (query == null || query.trim().length() < 2) {
            return List.of(); // Return empty list
        }
        return countryService.findCountryByNameContainingIgnoreCase(query.trim());
    }
}