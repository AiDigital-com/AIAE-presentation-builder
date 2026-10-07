package com.aidigital.reportconstructor.externalservices.clerk;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the Clerk properties binding for the external-services module. */
@Configuration
@EnableConfigurationProperties(ClerkProperties.class)
public class ExternalServicesClerkConfig {

}
