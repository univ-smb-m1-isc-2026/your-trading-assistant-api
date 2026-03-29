package fr.info803.trading_assistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.dto.ProfileResponse;
import fr.info803.trading_assistant.dto.UpdateProfileRequest;
import fr.info803.trading_assistant.dto.UpdateProfileResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(accountService.getProfile(account));
    }

    @PutMapping
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @AuthenticationPrincipal Account account,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(accountService.updateProfile(account, request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteProfile(@AuthenticationPrincipal Account account) {
        accountService.deleteProfile(account);
        return ResponseEntity.noContent().build();
    }
}
