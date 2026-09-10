package com.community.iam_service.services;

import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    @Transactional
    public void changePassword(String userId, String oldPwd, String newPwd) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException.UserNotFoundException(userId));

        if (!encoder.matches(oldPwd, user.getPassword())) {
            throw new AppException.WrongPasswordException();
        }

        user.setPassword(encoder.encode(newPwd));
        userRepo.save(user);
    }
}
