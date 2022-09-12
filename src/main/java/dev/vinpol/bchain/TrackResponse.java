package dev.vinpol.bchain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrackResponse(
        @JsonProperty("value")
        String id,
        @JsonProperty("artist")
        String artist,
        @JsonProperty("title")
        String title,
        @JsonProperty("image")
        String imageUrl
) {
    @JsonProperty("label")
    String label() {
        return artist + " - " + title;
    }
}
