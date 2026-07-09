package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecipientGroupMemberDto {

    @NotBlank(message = "Member email must not be blank")
    @Email(message = "Must be a valid email address")
    private String email;
}
