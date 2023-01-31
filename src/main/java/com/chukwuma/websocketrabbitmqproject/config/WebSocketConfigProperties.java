package com.chukwuma.websocketrabbitmqproject.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "websocket")
@Getter
@Setter
public class WebSocketConfigProperties {

    List<BrokerAddress> addresses;

    @Getter
    @Setter
    static class BrokerAddress {
        private int port;
        private String host;
        private boolean ssl;
    }
}
