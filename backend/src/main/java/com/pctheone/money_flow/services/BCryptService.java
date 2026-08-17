package com.pctheone.money_flow.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class BCryptService {
    @Autowired
    PasswordEncoder bCryptPasswordEncoder;

    public BCryptService(PasswordEncoder bCryptPasswordEncoder) {
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    public String encodePassword(String pass){
        return bCryptPasswordEncoder.encode(pass);
    }
}
