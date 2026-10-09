package com.example.saccoapp;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private EditText etDateOfBirth;

    private Button btnRegister;
    private Button btnBackToLogin;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etDateOfBirth = findViewById(R.id.etDateOfBirth);

        btnRegister = findViewById(R.id.btnRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        etDateOfBirth.setOnClickListener(v -> {

            Calendar calendar = Calendar.getInstance();

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(
                    RegisterActivity.this,
                    (DatePicker view, int selectedYear,
                     int selectedMonth, int selectedDay) -> {

                        String dob = selectedDay + "/"
                                + (selectedMonth + 1)
                                + "/" + selectedYear;

                        etDateOfBirth.setText(dob);
                    },
                    year,
                    month,
                    day
            );

            datePicker.show();
        });

        btnRegister.setOnClickListener(v -> registerUser());

        btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {

        String fullName =
                etFullName.getText().toString().trim();

        String phone =
                etPhone.getText().toString().trim();

        String email =
                etEmail.getText().toString().trim();

        String password =
                etPassword.getText().toString().trim();

        String confirmPassword =
                etConfirmPassword.getText().toString().trim();

        String dob =
                etDateOfBirth.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Enter your full name");
            etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Enter your phone number");
            etPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter your email");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(dob)) {
            etDateOfBirth.setError("Select your date of birth");
            etDateOfBirth.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Enter a password");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError(
                    "Password must have at least 6 characters"
            );
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError(
                    "Passwords do not match"
            );
            etConfirmPassword.requestFocus();
            return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("Creating Account...");

        firebaseAuth
                .createUserWithEmailAndPassword(
                        email,
                        password
                )
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {

                        btnRegister.setEnabled(true);
                        btnRegister.setText("Register");

                        String message =
                                "Registration failed";

                        if (task.getException() != null) {
                            message =
                                    task.getException().getMessage();
                        }

                        Toast.makeText(
                                RegisterActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    FirebaseUser user =
                            firebaseAuth.getCurrentUser();

                    if (user == null) {

                        btnRegister.setEnabled(true);
                        btnRegister.setText("Register");

                        Toast.makeText(
                                RegisterActivity.this,
                                "User account was not found",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    String uid = user.getUid();

                    String memberNumber =
                            generateMemberNumber();

                    Map<String, Object> member =
                            new HashMap<>();

                    member.put(
                            "memberNumber",
                            memberNumber
                    );
                    member.put("role", "member");

                    member.put(
                            "fullName",
                            fullName
                    );

                    member.put(
                            "phone",
                            phone
                    );

                    member.put(
                            "email",
                            email
                    );

                    member.put(
                            "dateOfBirth",
                            dob
                    );

                    member.put(
                            "registrationDate",
                            System.currentTimeMillis()
                    );

                    firestore
                            .collection("members")
                            .document(uid)
                            .set(member)
                            .addOnSuccessListener(aVoid -> {

                                Toast.makeText(
                                        RegisterActivity.this,
                                        "Account and member profile created successfully!",
                                        Toast.LENGTH_LONG
                                ).show();

                                finish();
                            })
                            .addOnFailureListener(e -> {

                                btnRegister.setEnabled(true);
                                btnRegister.setText("Register");

                                Toast.makeText(
                                        RegisterActivity.this,
                                        "Firestore error: "
                                                + e.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                });
    }

    private String generateMemberNumber() {

        long timestamp =
                System.currentTimeMillis();

        return "SACCO-" +
                String.valueOf(timestamp)
                        .substring(5);
    }
}