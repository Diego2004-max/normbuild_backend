package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegulatoryTextChunkerTest {

    private final RegulatoryTextChunker chunker = new RegulatoryTextChunker();

    @Test
    void preservesShortDocumentsWithoutAddingChunks() {
        assertThat(chunker.chunk("Artículo 1. Norma aplicable al proyecto."))
                .containsExactly("Artículo 1. Norma aplicable al proyecto.");
    }

    @Test
    void boundsLongChunksAndKeepsOverlapBetweenAdjacentSections() {
        String text = ("Artículo vigente sobre licencias urbanísticas y requisitos del predio.\n\n").repeat(400);

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(6000));
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1);
            String current = chunks.get(index);
            assertThat(previous.substring(previous.length() - 300)).isSubstringOf(current);
        }
    }

    @Test
    void ignoresEmptyDocuments() {
        assertThat(chunker.chunk(" \n\t ")).isEmpty();
    }

    @Test
    void assignsArticleReferencesFromExtractedLegalHeadings() {
        String text = "Acuerdo municipal\n"
                + "Artículo 1. Objeto del acuerdo. Define el instrumento territorial.\n\n"
                + "ARTÍCULO 2. Uso del suelo. El uso permitido se verifica con la ficha normativa.\n\n"
                + "Artículo 3. Vigencia. El acuerdo rige desde su publicación.";

        var chunks = chunker.chunkWithReferences(text);

        assertThat(chunks).extracting(RegulatoryTextChunker.RegulatoryChunk::articleReference)
                .containsExactly("Artículo 1", "Artículo 2", "Artículo 3");
        assertThat(chunks.get(0).content()).contains("Artículo 1");
        assertThat(chunks.get(1).content()).contains("ARTÍCULO 2");
        assertThat(chunks.get(2).content()).contains("Artículo 3");
    }
}
