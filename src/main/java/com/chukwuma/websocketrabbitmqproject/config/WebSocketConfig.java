package com.chukwuma.websocketrabbitmqproject.config;

import com.google.common.collect.Iterables;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.messaging.simp.stomp.StompReactorNettyCodec;
import org.springframework.messaging.tcp.reactor.ReactorNettyTcpClient;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.TcpClient;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketConfigProperties webSocketConfigProperties;

    private final StompErrorHandler stompErrorHandler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setTcpClient(createTcpClient())
                .setUserDestinationBroadcast("/topic/simp-user-registry")
                .setSystemHeartbeatReceiveInterval(10000)
                .setSystemHeartbeatSendInterval(10000)
                .setSystemLogin("guest")
                .setSystemPasscode("guest")
                .setClientLogin("guest")
                .setClientPasscode("guest")
                .setVirtualHost("/")
                .setUserRegistryBroadcast("/topic/log-user-registry");

        registry.setApplicationDestinationPrefixes("/api");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);

    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .setErrorHandler(stompErrorHandler)
                .addEndpoint("/websocket")
                .setAllowedOrigins("*")
                .withSockJS();
    }

//    @Override
//    public void configureClientInboundChannel(ChannelRegistration registration) {
//        WebSocketMessageBrokerConfigurer.super.configureClientInboundChannel(registration);
//    }

    private ReactorNettyTcpClient<byte[]> createTcpClient() {

        Map<InetSocketAddress, Boolean> addressToSecurityMap = new HashMap<>();
        List<InetSocketAddress> listOfAddresses = new ArrayList<>();

        // Create InetAddresses for each broker that is used
        List<WebSocketConfigProperties.BrokerAddress> addresses = webSocketConfigProperties.getAddresses();
        addresses.forEach(address -> {
            log.info("**** ADDING STOMP BROKER [ {} ] TO THE CLUSTER LIST ****", address.getHost());
            InetSocketAddress inetSocketAddress = new InetSocketAddress(address.getHost(), address.getPort());
            listOfAddresses.add(inetSocketAddress);
            addressToSecurityMap.put(inetSocketAddress, address.isSsl());
        });

        Iterator<InetSocketAddress> roundRobinAddressList = Iterables.cycle(listOfAddresses).iterator();

        UnaryOperator<TcpClient> contextAddressFunction = tcpClient -> {
            InetSocketAddress address = roundRobinAddressList.next();
            Boolean isSecure = addressToSecurityMap.getOrDefault(address, false);
            TcpClient remoteAddress = tcpClient.remoteAddress(() -> address);
            return isSecure ? remoteAddress.secure(SslProvider.defaultClientProvider()) : remoteAddress;
        };

        return new ReactorNettyTcpClient<>(contextAddressFunction, new StompReactorNettyCodec());
    }


}
