package com.tickets.compra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CompraServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompraServiceApplication.class, args);
	}
}