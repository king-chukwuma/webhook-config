package com.chukwuma.websocketrabbitmqproject.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.security.Principal;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    private final MessageChannel clientOutboundChannel;

    private final Supplier<GenericResponse<Object>> genericResponseSupplier = () -> {
        GenericResponse<Object> genericResponse = new GenericResponse<>();
        genericResponse.setMessage("Failed Message");
        genericResponse.setStatus("Failed Message");
        genericResponse.setStatusCode(500);
        return  genericResponse;
    };

    @Override
    @Nullable
    public Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        return handleClientMessageException(clientMessage, ex);
    }

    private Message<byte[]> handleClientMessageException(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        StompHeaderAccessor errorStompHeaderAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorStompHeaderAccessor.setMessage(ex.getMessage());
        errorStompHeaderAccessor.setLeaveMutable(true);

        GenericResponse<Object> genericResponse = genericResponseSupplier.get();
        genericResponse.setData(ex.getMessage());

        StompHeaderAccessor clientHeaderAccessor = null;

        enrichResponse(genericResponse, ex);

        if(clientMessage != null) {
            genericResponse.setData(new String(clientMessage.getPayload(), StandardCharsets.UTF_8));

            clientHeaderAccessor = MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);

            if(clientHeaderAccessor != null){
                String clientReceipt = clientHeaderAccessor.getReceipt();
                String clientSessionId = clientHeaderAccessor.getSessionId();
                Principal user = clientHeaderAccessor.getUser();

                if(clientReceipt != null) errorStompHeaderAccessor.setReceiptId(clientReceipt);

                if(clientSessionId != null) errorStompHeaderAccessor.setSessionId(clientSessionId);

                if(user != null && StringUtils.hasText(user.getName())) genericResponse.setMessage(String.format("%s: [%s]", genericResponse.getMessage(), user.getName()));
            }
        }

        byte[] responseAsBytes = serializeResponseAsBytes(genericResponse);

        return handleInternal(errorStompHeaderAccessor, responseAsBytes, ex, clientHeaderAccessor);
    }

    private void enrichResponse(GenericResponse<Object> genericResponse, Throwable ex) {

        String defaultMessage = ex.getMessage();


        Predicate<Class<? extends Throwable>> verifyException = clazz -> ex.getClass() != null && clazz.isInstance(ex.getCause());

        if(ex instanceof AccessDeniedException) {
            genericResponse.setStatusCode(903);
            genericResponse.setMessage(String.format("%s : [code - %s] %s, Access has been denied", HttpStatus.UNAUTHORIZED.name(), HttpStatusCode.valueOf(401), defaultMessage));
        }

        else if(ex instanceof ValidationException) {
            genericResponse.setStatusCode(400);
            genericResponse.setMessage(String.format("%s : code - %s %s", HttpStatus.BAD_REQUEST, 400,  defaultMessage));
        }

        else {
            genericResponse.setMessage(defaultMessage);
            genericResponse.setData(ex.getCause());
        }
    }

    private byte[] serializeResponseAsBytes(Object object) {
        byte[] responseAsBytes;

        try {
            responseAsBytes = objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize Generic response");
            try {
                log.info("Using the default byte[] response");
                String defaultStringResponse = String.valueOf(object);
                responseAsBytes = objectMapper.writeValueAsBytes(defaultStringResponse);
            } catch (JsonProcessingException exc) {
                throw new RuntimeException(exc);
            }
        }
        return responseAsBytes;
    }

    @Override
    @Nullable
    public Message<byte[]> handleErrorMessageToClient(Message<byte[]> errorMessage) {
        StompHeaderAccessor errorStompHeaderAccessor = MessageHeaderAccessor.getAccessor(errorMessage, StompHeaderAccessor.class);
        Assert.notNull(errorMessage, "No StompHeaderAccessor");

        assert errorStompHeaderAccessor != null;
        if(errorStompHeaderAccessor.isMutable()) {
            errorStompHeaderAccessor = StompHeaderAccessor.wrap(errorMessage);
        }
        GenericResponse<Object> genericResponse = genericResponseSupplier.get();
        genericResponse.setData(new String(errorMessage.getPayload(), StandardCharsets.UTF_8));

        byte[] responseAsBytes = serializeResponseAsBytes(genericResponse);

        return handleInternal(errorStompHeaderAccessor, responseAsBytes, null, null);
    }

    public void sendErrorBodyToChannel(@Nullable StompHeaderAccessor accessor, Throwable throwable) {
        GenericResponse<Object> genericResponse = genericResponseSupplier.get();
        enrichResponse(genericResponse, throwable);
        boolean isCustomException = isCustomException(throwable);

        StompHeaderAccessor errorHeaderAccessor = StompHeaderAccessor.create(StompCommand.ERROR);

        if(!isCustomException) {
            genericResponse.setStatus("Failed");
            errorHeaderAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        }
        errorHeaderAccessor.setMessage(genericResponse.getMessage());
        errorHeaderAccessor.setLeaveMutable(true);
        if (accessor != null) {
            errorHeaderAccessor.setSessionId(accessor.getSessionId());
        }

        byte[] responseAsBytes = serializeResponseAsBytes(genericResponse);

        clientOutboundChannel.send(MessageBuilder.createMessage(responseAsBytes, errorHeaderAccessor.getMessageHeaders()));
    }

    private boolean isCustomException(Throwable ex) {
        return ex != null && !(ex instanceof ValidationException);
    }
}
