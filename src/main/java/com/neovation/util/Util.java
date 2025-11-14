package com.neovation.util;

import io.jsonwebtoken.lang.Maps;

import java.util.Map;

public class Util {
    public static final Map<String, String> serviceMap = Map.of(
            "Web Development", "WEB_DEVELOPMENT",
            "Data Management", "DATA_MANAGEMENT",
            "ETL Process", "ETL_PROCESS",
            "Cloud Hosting", "CLOUD_HOSTING",
            "Custom Inquiry", "CUSTOM_INQUIRY" // Corrected typo from "Inquery"
    );
}
