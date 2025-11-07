package com.template.springboottemplate.repository;

import com.template.springboottemplate.model.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {
}