package com.tickets.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activa el @Scheduled en SchedulerService
@SpringBootApplication
@EnableScheduling
public class SchedulerServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(SchedulerServiceApplication.class, args);
	}
}