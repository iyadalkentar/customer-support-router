package com.ikdev.customersupportrouter.aiclassifierservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiClassifierServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiClassifierServiceApplication.class, args);
	}

}
