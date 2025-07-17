package com.example.restproject.repository;

import com.example.restproject.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String filePath = "src/main/resources/data/users.json";

    public List<User> findAll() {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(file, new TypeReference<List<User>>() {});
        } catch (IOException e) {
            logger.error("Error reading users from JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<User> findById(Long id) {
        return findAll().stream().filter(user -> user.id().equals(id)).findFirst();
    }

    public User save(User user) {
        List<User> users = findAll();
        users.removeIf(u -> u.id().equals(user.id()));
        users.add(user);
        saveToFile(users);
        return user;
    }

    public void delete(Long id) {
        List<User> users = findAll();
        users.removeIf(user -> user.id().equals(id));
        saveToFile(users);
    }

    private void saveToFile(List<User> users) {
        try {
            objectMapper.writeValue(new File(filePath), users);
            logger.info("Users saved to JSON file");
        } catch (IOException e) {
            logger.error("Error saving users to JSON: {}", e.getMessage());
        }
    }
}
