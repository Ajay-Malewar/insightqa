package com.ajaymalewar.insightqa.dto;

import java.util.List;

public record QaResponse(String answer, List<SourceChunk> sources) {

    public record SourceChunk(String fileName, String snippet) {
    }
}