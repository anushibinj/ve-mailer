package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponseWrapper {

    private boolean success;
    private String message;

    public static ApiResponseWrapper success(String message) {
        return ApiResponseWrapper.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ApiResponseWrapper error(String message) {
        return ApiResponseWrapper.builder()
                .success(false)
                .message(message)
                .build();
    }
}
