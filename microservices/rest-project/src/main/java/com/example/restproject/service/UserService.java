package com.example.restproject.service;

import com.example.restproject.dto.UserDTO;
import com.example.restproject.model.User;
import com.example.restproject.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserDTO> getAllUsers() {
        logger.info("Fetching all users");
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public Optional<UserDTO> getUserById(Long id) {
        logger.info("Fetching user with id: {}", id);
        return userRepository.findById(id)
                .map(this::convertToDTO);
    }

    public UserDTO createUser(UserDTO userDTO) {
        logger.info("Creating new user: {}", userDTO.name());
        User user = new User(
                userDTO.id() != null ? userDTO.id() : System.currentTimeMillis(),
                userDTO.name(),
                userDTO.email(),
                userDTO.role()
        );
        return convertToDTO(userRepository.save(user));
    }

    public Optional<UserDTO> updateUser(Long id, UserDTO userDTO) {
        logger.info("Updating user with id: {}", id);
        return userRepository.findById(id)
                .map(existingUser -> {
                    User updatedUser = new User(
                            id,
                            userDTO.name(),
                            userDTO.email(),
                            userDTO.role()
                    );
                    return convertToDTO(userRepository.save(updatedUser));
                });
    }

    public Optional<UserDTO> patchUser(Long id, UserDTO userDTO) {
        logger.info("Patching user with id: {}", id);
        return userRepository.findById(id)
                .map(existingUser -> {
                    User updatedUser = new User(
                            id,
                            userDTO.name() != null ? userDTO.name() : existingUser.name(),
                            userDTO.email() != null ? userDTO.email() : existingUser.email(),
                            userDTO.role() != null ? userDTO.role() : existingUser.role()
                    );
                    return convertToDTO(userRepository.save(updatedUser));
                });
    }

    public boolean deleteUser(Long id) {
        logger.info("Deleting user with id: {}", id);
        if (userRepository.findById(id).isPresent()) {
            userRepository.delete(id);
            return true;
        }
        return false;
    }

    private UserDTO convertToDTO(User user) {
        return new UserDTO(user.id(), user.name(), user.email(), user.role());
    }
}
