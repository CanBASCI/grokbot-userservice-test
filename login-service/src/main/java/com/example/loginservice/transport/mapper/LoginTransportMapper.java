package com.example.loginservice.transport.mapper;

import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.transport.dto.TokenResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LoginTransportMapper {

    TokenResponse toResponse(TokenPair pair);
}
