package shop.auth.service;


import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import shop.auth.model.Role;
import shop.auth.model.User;
import shop.auth.model.dto.UserDTO;
import shop.auth.model.dto.exceptions.RegistrationException;
import shop.auth.repository.UserRepository;
import shop.event.UserRegisteredEvent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private static final String USERNAME_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*$";
    private static final String EMAIL_PATTERN = "^[\\w-.]+@[\\w-]+(\\.[\\w-]+)*\\.[a-z]{2,}$";
    private static final String PHONE_PATTERN = "^\\+380\\d{9}$";

    public static final String USERNAME_TAKEN = "Username already taken";
    public static final String EMAIL_TAKEN = "Email already taken";
    public static final String PHONE_TAKEN = "Phone number already taken";

    public static final String INVALID_USERNAME = "Invalid username (start with letter, no spaces, min 8 char)";
    public static final String INVALID_PASSWORD = "Invalid password (min 8 char)";
    public static final String INVALID_EMAIL = "Invalid email";
    public static final String INVALID_PHONE = "Invalid phone";

    public User createAndSaveUser(UserDTO dto) throws RegistrationException {
        validateForRegistration(dto);

        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .email(dto.getEmail())
                .fullName(dto.getFullName())
                .phone(dto.getPhone())
                .roles(Set.of(Role.USER)) // default role assigned to user
                        .build();

        user = userRepository.saveAndFlush(user);

        //todo: logic to write UserRegisteredEvent

        var userRegisteredEvent = new UserRegisteredEvent(user.getId(), user.getUsername());

        CompletableFuture<SendResult<String, UserRegisteredEvent>> completableFuture =
                kafkaTemplate.send("user-registered-events-topic", String.valueOf(userRegisteredEvent.getUserId()), userRegisteredEvent);

        completableFuture.whenComplete((result, exception) -> {
            if (exception != null) {
                logger.error("Failed to send message: {}", exception.getMessage());
            } else {
                logger.info("Message sent successfully {}", result.getRecordMetadata());
            }
        });

        return user;
    }

    private void validateForRegistration(UserDTO dto) throws RegistrationException {
        if (dto.getUsername() == null || !dto.getUsername().matches(USERNAME_PATTERN) || dto.getUsername().length() < 8)
            throw new RegistrationException(INVALID_USERNAME);

        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new RegistrationException("Username already exists");
        }

        if (dto.getPassword() == null || dto.getPassword().length() < 8)
            throw new RegistrationException(INVALID_PASSWORD);

        if (dto.getEmail() == null || !dto.getEmail().matches(EMAIL_PATTERN))
            throw new RegistrationException(INVALID_EMAIL);

        if (dto.getPhone() == null || !dto.getPhone().matches(PHONE_PATTERN))
            throw new RegistrationException(INVALID_PHONE);

        if (userRepository.existsByUsername(dto.getUsername()))
            throw new RegistrationException(USERNAME_TAKEN);

        if (userRepository.existsByEmail(dto.getEmail()))
            throw new RegistrationException(EMAIL_TAKEN);

        if (userRepository.existsByPhone(dto.getPhone()))
            throw new RegistrationException(PHONE_TAKEN);
    }
}