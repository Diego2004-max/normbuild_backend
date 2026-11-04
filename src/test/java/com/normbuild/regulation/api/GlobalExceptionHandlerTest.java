package com.normbuild.regulation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RequestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsBadRequestForMalformedUtf8Json() throws Exception {
        byte[] invalidUtf8Json = {
                0x7B, 0x22, 0x6E, 0x61, 0x6D, 0x65, 0x22, 0x3A, 0x22,
                (byte) 0xF1,
                0x22, 0x7D
        };

        mockMvc.perform(post("/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUtf8Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("El cuerpo de la solicitud debe ser un JSON válido codificado en UTF-8."));
    }

    @RestController
    private static class RequestController {

        @PostMapping("/request")
        public String accept(@RequestBody Map<String, String> request) {
            return "accepted";
        }
    }
}