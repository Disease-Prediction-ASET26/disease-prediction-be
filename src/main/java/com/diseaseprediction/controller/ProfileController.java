package com.diseaseprediction.controller;

import com.diseaseprediction.api.ProfileApi;
import com.diseaseprediction.api.model.ConsentRequest;
import com.diseaseprediction.api.model.CreateProfileRequest;
import com.diseaseprediction.api.model.Profile;
import com.diseaseprediction.service.profile.ProfileService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's profile and consent (MDD Iteration 6). Implements the generated {@link
 * ProfileApi} from {@code openapi/profile-api.yaml} and delegates to {@link ProfileService}.
 */
@RestController
public class ProfileController implements ProfileApi {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public ResponseEntity<Profile> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @Override
    public ResponseEntity<Profile> createProfile(CreateProfileRequest createProfileRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileService.createPatientProfile(createProfileRequest));
    }

    @Override
    public ResponseEntity<Profile> recordConsent(ConsentRequest consentRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileService.recordConsent(consentRequest));
    }
}
