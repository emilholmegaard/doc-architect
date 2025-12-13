package com.example.controller;

import com.example.dto.UserDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public List<UserDto> getAllUsers() {
        return List.of();
    }

    @GetMapping("/{id}")
    public UserDto getUserById(@PathVariable Long id) {
        return null;
    }

    @PostMapping
    public UserDto createUser(@RequestBody UserDto user) {
        return user;
    }

    @PutMapping("/{id}")
    public UserDto updateUser(@PathVariable Long id, @RequestBody UserDto user) {
        return user;
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        // Delete logic
    }

    @GetMapping("/search")
    public List<UserDto> searchUsers(@RequestParam String name, @RequestParam(required = false) Integer age) {
        return List.of();
    }
}
