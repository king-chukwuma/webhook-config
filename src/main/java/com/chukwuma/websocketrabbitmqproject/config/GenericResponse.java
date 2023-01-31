package com.chukwuma.websocketrabbitmqproject.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenericResponse<T> {

    private int statusCode;
    private String status;
    private String message;
    private T data;
}
