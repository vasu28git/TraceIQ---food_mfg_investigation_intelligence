package com.investigation.platform.user.service;

import com.investigation.platform.common.dto.PageResponse;
import com.investigation.platform.user.dto.request.CreateUserRequest;
import com.investigation.platform.user.dto.request.UpdateUserRequest;
import com.investigation.platform.user.dto.response.UserResponse;
import com.investigation.platform.user.enums.UserStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUserById(UUID userId);

    UserResponse updateUser(UUID userId, UpdateUserRequest request);

    void deleteUser(UUID userId);

    PageResponse<UserResponse> getAllUsers(Pageable pageable);

    PageResponse<UserResponse> getUsersByStatus(UserStatus status, Pageable pageable);
}
