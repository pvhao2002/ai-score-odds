package com.app.kira.dto.predict;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProxyDTO {
    private String server;
    private int port;
    private String secret;


}
