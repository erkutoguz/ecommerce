package dev.erkut.orderworkflowservice;

import org.springframework.boot.SpringApplication;

public class TestOrderWorkflowServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(OrderWorkflowServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
