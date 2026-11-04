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
}
