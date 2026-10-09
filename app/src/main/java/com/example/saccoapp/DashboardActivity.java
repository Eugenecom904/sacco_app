package com.example.saccoapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;


public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private TextView tvMemberNumber;

    private Button btnLogout;
    private Button btnAdmin;
    private Button btnSavings;
    private Button btnLoans;
    private Button btnRepayments;
    private Button btnTransactions;
    private Button btnProfile;


    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Connect dashboard views
        tvWelcome = findViewById(R.id.tvWelcome);
        tvMemberNumber = findViewById(R.id.tvMemberNumber);
        btnLogout = findViewById(R.id.btnLogout);
        btnSavings = findViewById(R.id.btnSavings);
        btnLoans = findViewById(R.id.btnLoans);
        btnRepayments = findViewById(R.id.btnRepayments);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnProfile = findViewById(R.id.btnProfile);
        btnAdmin = findViewById(R.id.btnAdmin);

        btnAdmin.setVisibility(View.GONE);
        btnProfile.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DashboardActivity.this,
                            ProfileActivity.class
                    );

            startActivity(intent);
        });
        btnAdmin.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DashboardActivity.this,
                            AdminActivity.class
                    );

            startActivity(intent);
        });



        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Load member information
        loadMemberInformation();

        // Logout
        btnLogout.setOnClickListener(v -> logoutUser());
        btnSavings.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DashboardActivity.this,
                            SavingsActivity.class
                    );

            startActivity(intent);
        });
        btnLoans.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DashboardActivity.this,
                            LoanActivity.class
                    );

            startActivity(intent);
        });
        btnRepayments.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DashboardActivity.this,
                            RepaymentActivity.class
                    );

            startActivity(intent);
        });
        btnTransactions.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DashboardActivity.this,
                            TransactionsActivity.class
                    );

            startActivity(intent);
        });
    }

    private void loadMemberInformation() {

        // Get currently logged-in user
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {

            // No user is logged in
            goToLogin();
            return;
        }

        // Get Firebase UID
        String uid = currentUser.getUid();

        // Find member in Firestore
        firestore
                .collection("members")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        // Get member name
                        String fullName =
                                documentSnapshot.getString("fullName");

                        // Get membership number
                        String memberNumber =
                                documentSnapshot.getString("memberNumber");

                        // Display name
                        if (fullName != null &&
                                !fullName.isEmpty()) {

                            tvWelcome.setText(
                                    "Welcome, " + fullName
                            );
                            String role = documentSnapshot.getString("role");

                            if ("admin".equals(role)) {

                                btnAdmin.setVisibility(View.VISIBLE);

                            }
                        }

                        // Display membership number
                        if (memberNumber != null &&
                                !memberNumber.isEmpty()) {

                            tvMemberNumber.setText(
                                    "Member Number: " +
                                            memberNumber
                            );
                        }

                    } else {

                        Toast.makeText(
                                DashboardActivity.this,
                                "Member information not found",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            DashboardActivity.this,
                            "Unable to load member information: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void logoutUser() {

        // Sign out from Firebase
        firebaseAuth.signOut();

        Toast.makeText(
                DashboardActivity.this,
                "Logged out successfully",
                Toast.LENGTH_SHORT
        ).show();

        // Return to login
        goToLogin();
    }

    private void goToLogin() {

        Intent intent =
                new Intent(
                        DashboardActivity.this,
                        MainActivity.class
                );

        // Clear dashboard from back stack
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}