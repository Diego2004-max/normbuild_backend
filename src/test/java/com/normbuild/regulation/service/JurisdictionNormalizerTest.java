package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JurisdictionNormalizerTest {

    @Test
    void canonicalizesAccentsAliasesAndWhitespace() {
        assertThat(JurisdictionNormalizer.canonicalize(" Bogota  D.C. ")).isEqualTo("Bogotá D.C.");
        assertThat(JurisdictionNormalizer.canonicalize("Santiago de Cali")).isEqualTo("Cali");
        assertThat(JurisdictionNormalizer.canonicalize("San Juan de Pasto")).isEqualTo("Pasto");
        assertThat(JurisdictionNormalizer.canonicalize("Cartagena de Indias")).isEqualTo("Cartagena");
    }

    @Test
    void preservesUnknownJurisdictionWithoutGuessing() {
        assertThat(JurisdictionNormalizer.canonicalize("  Villa de Leyva ")).isEqualTo("Villa de Leyva");
    }

    @Test
    void rejectsBlankJurisdiction() {
        assertThatThrownBy(() -> JurisdictionNormalizer.canonicalize("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
