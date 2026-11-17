package com.normbuild.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(@NotBlank @Size(max = 10_000) String credential) { }
