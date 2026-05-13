package com.musicstreaming.playlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class PlaylistServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlaylistServiceApplication.class, args);
    }
}
