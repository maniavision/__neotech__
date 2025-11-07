package com.neovation.repository;

import com.neovation.model.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CountryRepository extends JpaRepository<Country, String> {
    List<Country> findCountryByNameContainingIgnoreCase(String name);
}