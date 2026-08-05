package org.example.traveljava;

import org.example.traveljava.config.BookingProperties;
import org.example.traveljava.config.MqProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({MqProperties.class, BookingProperties.class})
public class TravelJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelJavaApplication.class, args);
    }

}
