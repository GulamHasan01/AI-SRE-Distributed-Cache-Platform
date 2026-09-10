package com.community.iam_service.services;

import com.community.iam_service.dtos.request.UpdateUserRequest;
import com.community.iam_service.dtos.request.UpdateUserSelfRequest;
import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException.UserNotFoundException(userId));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException.UserNotFoundException(email));
    }

    public User getPublicById(String userId) {
        User user = getById(userId);
        if (user.getStatus() == UserStatus.DELETED || user.isDeactivated()) {
            throw new AppException.UserNotFoundException(userId);
        }
        return user;
    }

    public User updateSelf(String userId, UpdateUserSelfRequest req) {

        User user = getById(userId);

        if (req.getName().isPresent()) {
            user.setName(req.getName().orElse(null));
        }

        if (req.getUsername().isPresent()) {
            String username = req.getUsername().orElse(null);

            if (username != null && userRepository.existsByUsername(username)) {
                throw new RuntimeException("Username already taken");
            }
            user.setProfileCompletionScore(calculateScore(user));

            user.setUsername(username);
        }

        return userRepository.save(user);
    }
    @Transactional
    public User updateUser(String userId, UpdateUserRequest req) {
        User user = getById(userId);

        if (req.getUsername() != null) {
            String username = req.getUsername().toLowerCase().trim();
            if (!username.equals(user.getUsername()) &&
                    userRepository.existsByUsername(username)) {
                throw new AppException("Username already taken", org.springframework.http.HttpStatus.CONFLICT);
            }
            user.setUsername(username);
        }

        if (req.getEmail() != null) {
            String email = req.getEmail().toLowerCase().trim();
            if (!email.equals(user.getEmail()) &&
                    userRepository.existsByEmail(email)) {
                throw new AppException.EmailAlreadyExistsException(email);
            }
            user.setEmail(email);
            user.setEmailVerified(false);
        }

        if (req.getName().isPresent()) {
            user.setName(req.getName().orElse(null));
        }
        if (req.getRoles().isPresent()) {
            user.setRoles(req.getRoles().orElse(List.of(Role.USER)));
        }

        if (req.getVerified().isPresent()) {
            user.setVerified(req.getVerified().orElse(false));
        }

        if (req.getStatus().isPresent()) {
            user.setStatus(req.getStatus().orElse(UserStatus.ACTIVE));
        }

        if (req.getEnabled().isPresent()) {
            user.setEnabled(req.getEnabled().orElse(true));
        }

        if (req.getDeactivated().isPresent()) {
            boolean deactivated = req.getDeactivated().orElse(false);
            user.setDeactivated(deactivated);

            if (deactivated) {
                user.setDeactivatedAt(Instant.now());
            } else {
                user.setDeactivatedAt(null);
            }
        }

        user.setProfileCompletionScore(calculateScore(user));
        return userRepository.save(user);
    }

    public List<Role> getRoles(String userId) {
        return getById(userId).getRoles();
    }

    @Transactional
    public void softDelete(String userId) {
        User user = getById(userId);
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(String userId) {
        User user = getById(userId);
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeactivated(true);
        user.setDeactivatedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public void setVerified(String userId, boolean verified) {
        User user = getById(userId);
        user.setVerified(verified);
        userRepository.save(user);
    }

    public int calculateScore(User user) {
        int score = 0;
        if (user.getEmail() != null)         score += 20;
        if (user.getUsername() != null)      score += 20;
        if (user.isEmailVerified())          score += 20;
        if (user.getReferredBy() != null)    score += 10;
        return Math.min(score, 70);
    }
}