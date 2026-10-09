package com.example.saccoapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvProfileName;
    private TextView tvProfileMemberNumber;
    private TextView tvProfilePhone;
    private TextView tvProfileEmail;
    private TextView tvProfileDob;
    private TextView tvProfileRegistrationDate;

    private Button btnChangePassword;
    private Button btnBackProfile;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Connect XML elements
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileMemberNumber = findViewById(R.id.tvProfileMemberNumber);
        tvProfilePhone = findViewById(R.id.tvProfilePhone);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileDob = findViewById(R.id.tvProfileDob);
        tvProfileRegistrationDate = findViewById(R.id.tvProfileRegistrationDate);

        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnBackProfile = findViewById(R.id.btnBackProfile);

        // Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Load profile information
        loadProfile();

        // Change password
        btnChangePassword.setOnClickListener(v -> changePassword());

        // Back button
        btnBackProfile.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        Log.e("PROFILE_DEBUG", "ProfileActivity is being destroyed");
        super.onDestroy();
    }

    private void loadProfile() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(
                    this,
                    "Please login first",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        String uid = currentUser.getUid();

        // Show the email from Firebase Authentication immediately.
        if (currentUser.getEmail() != null) {
            tvProfileEmail.setText(currentUser.getEmail());
        }

        // Load the member's Firestore document.
        firestore.collection("members")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (documentSnapshot.exists()) {

                        String name = documentSnapshot.getString("fullName");
                        String memberNumber = documentSnapshot.getString("memberNumber");
                        String phone = documentSnapshot.getString("phone");
                        String email = documentSnapshot.getString("email");
                        String dob = documentSnapshot.getString("dateOfBirth");
                        Object registrationDateValue =
                                documentSnapshot.get("registrationDate");

                        String registrationDate = "Not provided";

                        if (registrationDateValue instanceof String) {

                            registrationDate =
                                    (String) registrationDateValue;

                        } else if (registrationDateValue instanceof Timestamp) {

                            Timestamp timestamp =
                                    (Timestamp) registrationDateValue;

                            registrationDate =
                                    new SimpleDateFormat(
                                            "dd MMM yyyy",
                                            Locale.getDefault()
                                    ).format(timestamp.toDate());

                        } else if (registrationDateValue instanceof Long) {

                            registrationDate =
                                    new SimpleDateFormat(
                                            "dd MMM yyyy",
                                            Locale.getDefault()
                                    ).format(
                                            new java.util.Date(
                                                    (Long) registrationDateValue
                                            )
                                    );
                        }

                        tvProfileName.setText(name != null ? name : "Not provided");
                        tvProfileMemberNumber.setText(memberNumber != null ? memberNumber : "Not provided");
                        tvProfilePhone.setText(phone != null ? phone : "Not provided");
                        tvProfileEmail.setText(email != null ? email : currentUser.getEmail());
                        tvProfileDob.setText(dob != null ? dob : "Not provided");
                        tvProfileRegistrationDate.setText(registrationDate != null ? registrationDate : "Not provided");

                    } else {

                        Toast.makeText(
                                ProfileActivity.this,
                                "Member document not found in Firestore",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .addOnFailureListener(e -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            ProfileActivity.this,
                            "Profile loading failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void changePassword() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            String email = currentUser.getEmail();
            firebaseAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(aVoid -> Toast.makeText(
                            ProfileActivity.this,
                            "Password reset email sent to " + email,
                            Toast.LENGTH_LONG
                    ).show())
                    .addOnFailureListener(e -> Toast.makeText(
                            ProfileActivity.this,
                            "Failed to send password reset email: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show());
        } else {
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show();
        }
    }
}
