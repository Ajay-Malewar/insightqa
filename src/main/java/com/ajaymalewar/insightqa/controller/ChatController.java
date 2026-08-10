package com.ajaymalewar.insightqa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

private final ChatClient chatClient;

public ChatController(ChatClient.Builder chatClienBuilder){
    this.chatClient =chatClienBuilder.build();
}


@GetMapping("/chat")
public String chat(@RequestParam String question){

    return chatClient.prompt()
            .user(question)
            .call()
            .content();

}




}
