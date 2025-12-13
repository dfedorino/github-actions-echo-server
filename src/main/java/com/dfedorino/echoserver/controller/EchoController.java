package com.dfedorino.echoserver.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoController {
    @GetMapping(path = "/{string}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getEcho(@PathVariable String string) {
        return string;
    }
}
