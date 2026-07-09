package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class RecipientGroupUpdateDto {

    @NotBlank(message = "Group name must not be blank")
    @Size(max = 255, message = "Group name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /** Full replacement of the member list. */
    private Set<String> memberEmails = new LinkedHashSet<>();
}
