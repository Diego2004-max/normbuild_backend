package com.normbuild.account;

import java.util.UUID;

public record AccountProfile(UUID id, String email, String role) {
}
