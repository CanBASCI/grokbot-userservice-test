package com.example.signupservice.transport.mapper;

import com.example.signupservice.domain.model.User;
import com.example.signupservice.transport.dto.SignupResponse;
import com.example.signupservice.transport.dto.VerifyCredentialsResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SignupTransportMapper {

    @Mapping(target = "userId", source = "id")
    SignupResponse toSignupResponse(User user);

    @Mapping(target = "userId", source = "id")
    VerifyCredentialsResponse toVerifyResponse(User user);
}
