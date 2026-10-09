package com.example.saccoapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;

    // Firebase Authentication
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );

        // Connect XML fields
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);

        // Initialize Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance();

        // Login button
        btnLogin.setOnClickListener(v -> loginUser());

        // Register button
        btnRegister.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            MainActivity.this,
                            RegisterActivity.class
                    );

            startActivity(intent);
        });
    }

    private void loginUser() {

        String email =
                etEmail.getText().toString().trim();

        String password =
                etPassword.getText().toString().trim();

        // Check email
        if (email.isEmpty()) {

            etEmail.setError("Enter your email");
            etEmail.requestFocus();
            return;
        }

        // Check password
        if (password.isEmpty()) {

            etPassword.setError("Enter your password");
            etPassword.requestFocus();
            return;
        }

        // Disable login button while Firebase is processing
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        // Firebase login
        firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                MainActivity.this,
                                "Login successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        // Open dashboard
                        Intent intent =
                                new Intent(
                                        MainActivity.this,
                                        DashboardActivity.class
                                );

                        // Pass email to dashboard
                        intent.putExtra(
                                "email",
                                email
                        );

                        // Pass Firebase UID
                        if (firebaseAuth.getCurrentUser() != null) {

                            intent.putExtra(
                                    "uid",
                                    firebaseAuth
                                            .getCurrentUser()
                                            .getUid()
                            );
                        }

                        startActivity(intent);

                        // Prevent returning to login with Back button
                        finish();

                    } else {

                        btnLogin.setEnabled(true);
                        btnLogin.setText("Login");

                        String errorMessage =
                                "Invalid email or password";

                        if (task.getException() != null) {

                            String firebaseError =
                                    task.getException().getMessage();

                            if (firebaseError != null) {
                                errorMessage = firebaseError;
                            }
                        }

                        Toast.makeText(
                                MainActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}