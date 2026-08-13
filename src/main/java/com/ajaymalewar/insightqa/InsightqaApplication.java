package com.ajaymalewar.insightqa;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = { PgVectorStoreAutoConfiguration.class })
public class InsightqaApplication {

	public static void main(String[] args) {
		SpringApplication.run(InsightqaApplication.class, args);

		System.out.println("Application Running......!");

	}

}
