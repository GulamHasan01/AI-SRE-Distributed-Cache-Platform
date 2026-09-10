package com.community.iam_service.services;

import com.community.iam_service.entity.OAuthAccount;
import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import com.community.iam_service.entity.User;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.oauth.OAuthUserInfo;
import com.community.iam_service.repository.OAuthAccountRepository;
import com.community.iam_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;

    @Transactional
    public User findOrCreateUser(OAuthUserInfo info) {

        Optional<OAuthAccount> existingOAuth = oAuthAccountRepository
                .findByProviderAndProviderUserId(info.getProvider(), info.getProviderUserId());

        if (existingOAuth.isPresent()) {
            User user = userRepository.findById(existingOAuth.get().getUserId())
                    .orElseThrow(() -> new AppException.UserNotFoundException(existingOAuth.get().getUserId()));

            if (user.getStatus() == UserStatus.SUSPENDED) throw new AppException.AccountSuspendedException();
            if (user.getStatus() == UserStatus.DELETED)    throw new AppException.InvalidCredentialsException();

            return user;
        }

        Optional<User> existingUser = userRepository.findByEmail(info.getEmail().toLowerCase().trim());

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();

            if (user.getStatus() == UserStatus.SUSPENDED) throw new AppException.AccountSuspendedException();
            if (user.getStatus() == UserStatus.DELETED)   throw new AppException.InvalidCredentialsException();
        } else {
            user = userRepository.save(User.builder()
                    .email(info.getEmail().toLowerCase().trim())
                    .password(null)
                    .roles(List.of(Role.USER))
                    .provider(info.getProvider())
                    .emailVerified(true)
                    .build());
        }

        oAuthAccountRepository.save(OAuthAccount.builder()
                .userId(user.getId())
                .provider(info.getProvider())
                .providerUserId(info.getProviderUserId())
                .linkedAt(Instant.now())
                .build());

        return user;
    }
}
