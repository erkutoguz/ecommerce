package dev.erkut.orderworkflowservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderWorkflowServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
