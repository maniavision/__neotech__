package com.template.springboottemplate.service;

import com.template.springboottemplate.model.Country;
import com.template.springboottemplate.repository.CountryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CountryService {

    private final CountryRepository countryRepository;

    public CountryService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    public List<Country> getAllCountries() {
        return countryRepository.findAll();
    }

    public Optional<Country> getCountryByCode(String code) {
        return countryRepository.findById(code);
    }

    public Country saveCountry(Country country) {
        return countryRepository.save(country);
    }

    public void deleteCountry(String code) {
        countryRepository.deleteById(code);
    }

    public List<Country> findCountryByNameContainingIgnoreCase(String country) {
        return countryRepository.findCountryByNameContainingIgnoreCase(country);
    }
}
