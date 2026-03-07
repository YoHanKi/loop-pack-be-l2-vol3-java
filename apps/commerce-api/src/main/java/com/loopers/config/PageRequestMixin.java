package com.loopers.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.PageRequest;

@JsonIgnoreProperties(value = {"sort", "offset", "unpaged", "paged"}, ignoreUnknown = true)
abstract class PageRequestMixin {

    @JsonCreator
    static PageRequest of(
            @JsonProperty("pageNumber") int pageNumber,
            @JsonProperty("pageSize") int pageSize
    ) {
        return PageRequest.of(pageNumber, pageSize);
    }
}
