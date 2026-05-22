package com.project.flowfinserver.dto;

import lombok.Getter;

@Getter
public class PortfolioShareRequest {
    private Integer portfolioId;
    private String title;
    private String content;
}
