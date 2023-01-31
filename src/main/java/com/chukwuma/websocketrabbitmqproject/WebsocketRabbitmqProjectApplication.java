package com.chukwuma.websocketrabbitmqproject;

import com.chukwuma.websocketrabbitmqproject.config.WebSocketConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WebSocketConfigProperties.class)
public class WebsocketRabbitmqProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebsocketRabbitmqProjectApplication.class, args);
    }

}
