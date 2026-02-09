package com.restaurant.admin.security;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.SimpleUserRepository;

@Service
public class SimpleUserDetailsService implements UserDetailsService {

    @Autowired
    private SimpleUserRepository simpleUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        SimpleUser simpleUser = simpleUserRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return User.withUsername(simpleUser.getEmail())
            .password(simpleUser.getPassword())
            .authorities(new ArrayList<>())
            .build();
    }

}
