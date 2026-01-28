package com.velp.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParserStatusResponse {
    private String status;
    private int progress;
    private String videoId;
    private String error;
}
