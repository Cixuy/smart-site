package com.qst.smartbuildings;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "sensor.tcp.port=0")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
