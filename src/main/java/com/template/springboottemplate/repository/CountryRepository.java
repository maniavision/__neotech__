package com.template.springboottemplate.repository;

import com.template.springboottemplate.model.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CountryRepository extends JpaRepository<Country, String> {
    List<Country> findCountryByNameContainingIgnoreCase(String name);
}