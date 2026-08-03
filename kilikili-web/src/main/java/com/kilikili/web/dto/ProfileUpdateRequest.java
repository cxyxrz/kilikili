package com.kilikili.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 64) String nickname,
        @Pattern(regexp = "PRIVATE|MALE|FEMALE", message = "不支持的性别") String gender,
        LocalDate birthday,
        @Size(max = 200) String bio) { }
