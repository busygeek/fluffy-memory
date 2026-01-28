package com.example.restproject;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class RestProjectApplication {
    public static void main(String[] args) {

        SpringApplication.run(RestProjectApplication.class, args);
    }


    @Bean
    CommandLineRunner run(RestTemplate restTemplate) {
        return args -> {

            String url = "https://example.com/api/post";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("name", "Rama");
            body.put("age", 30);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, request, String.class);

            System.out.println(response.getStatusCode());
            System.out.println(response.getBody());
        };
    }
}
