package fr.info803.trading_assistant.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/* Contrôleur de test */
@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello World";
    }
}
