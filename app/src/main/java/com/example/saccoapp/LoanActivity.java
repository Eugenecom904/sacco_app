package com.example.saccoapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LoanActivity extends AppCompatActivity {

    private TextView tvLoanMember;
    private TextView tvLoanBalance;
    private TextView tvNoLoans;

    private LinearLayout loanHistoryContainer;

    private Button btnApplyLoan;
    private Button btnBack;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private double outstandingBalance = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_loan);

        tvLoanMember = findViewById(R.id.tvLoanMember);
        tvLoanBalance = findViewById(R.id.tvLoanBalance);
        tvNoLoans = findViewById(R.id.tvNoLoans);

        loanHistoryContainer =
                findViewById(R.id.loanHistoryContainer);

        btnApplyLoan =
                findViewById(R.id.btnApplyLoan);

        btnBack =
                findViewById(R.id.btnBack);

        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "Please login first",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        loadMemberInformation();
        loadLoans();

        btnApplyLoan.setOnClickListener(v ->
                showLoanApplicationDialog()
        );

        btnBack.setOnClickListener(v ->
                finish()
        );
    }

    // -----------------------------------------
    // MEMBER INFORMATION
    // -----------------------------------------

    private void loadMemberInformation() {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid =
                currentUser.getUid();

        firestore
                .collection("members")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        String memberNumber =
                                documentSnapshot.getString(
                                        "memberNumber"
                                );

                        if (memberNumber != null) {

                            tvLoanMember.setText(
                                    "Member Number: "
                                            + memberNumber
                            );
                        }
                    }
                });
    }

    // -----------------------------------------
    // LOAD LOANS
    // -----------------------------------------

    private void loadLoans() {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid =
                currentUser.getUid();

        firestore
                .collection("loans")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    outstandingBalance = 0.0;

                    loanHistoryContainer
                            .removeAllViews();

                    if (querySnapshot.isEmpty()) {

                        loanHistoryContainer
                                .addView(tvNoLoans);

                        tvLoanBalance.setText(
                                "KES 0.00"
                        );

                        return;
                    }

                    for (
                            DocumentSnapshot document :
                            querySnapshot
                    ) {

                        Double amount =
                                document.getDouble(
                                        "amount"
                                );

                        String status =
                                document.getString(
                                        "status"
                                );

                        String purpose =
                                document.getString(
                                        "purpose"
                                );

                        String loanType =
                                document.getString(
                                        "loanType"
                                );

                        String date =
                                document.getString(
                                        "date"
                                );

                        if (amount == null) {
                            amount = 0.0;
                        }

                        // Only approved loans count
                        // toward outstanding balance.
                        if (
                                "APPROVED".equals(status)
                        ) {

                            outstandingBalance +=
                                    amount;
                        }

                        addLoanToScreen(
                                loanType,
                                amount,
                                status,
                                purpose,
                                date
                        );
                    }

                    tvLoanBalance.setText(
                            String.format(
                                    "KES %.2f",
                                    outstandingBalance
                            )
                    );
                })
                .addOnFailureListener(e -> {

                    e.printStackTrace();

                    Toast.makeText(
                            LoanActivity.this,
                            "Loan loading error: "
                                    + e.getClass().getSimpleName()
                                    + "\n"
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // -----------------------------------------
    // DISPLAY LOAN
    // -----------------------------------------

    private void addLoanToScreen(
            String loanType,
            double amount,
            String status,
            String purpose,
            String date
    ) {

        LinearLayout loanLayout =
                new LinearLayout(this);

        loanLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        loanLayout.setPadding(
                20,
                20,
                20,
                20
        );

        TextView typeText =
                new TextView(this);

        typeText.setText(
                "Loan Type: " +
                        (loanType != null
                                ? loanType
                                : "General")
        );

        typeText.setTextSize(17);

        typeText.setTextColor(
                getResources().getColor(
                        android.R.color.black
                )
        );

        TextView amountText =
                new TextView(this);

        amountText.setText(
                String.format(
                        "Amount: KES %.2f",
                        amount
                )
        );

        amountText.setTextSize(17);

        TextView statusText =
                new TextView(this);

        statusText.setText(
                "Status: " +
                        (status != null
                                ? status
                                : "PENDING")
        );

        statusText.setTextSize(16);

        TextView purposeText =
                new TextView(this);

        purposeText.setText(
                "Purpose: " +
                        (purpose != null
                                ? purpose
                                : "")
        );

        purposeText.setTextSize(15);

        TextView dateText =
                new TextView(this);

        dateText.setText(
                "Date: " +
                        (date != null
                                ? date
                                : "")
        );

        dateText.setTextSize(13);

        dateText.setTextColor(
                getResources().getColor(
                        android.R.color.darker_gray
                )
        );

        loanLayout.addView(typeText);
        loanLayout.addView(amountText);
        loanLayout.addView(statusText);
        loanLayout.addView(purposeText);
        loanLayout.addView(dateText);

        loanHistoryContainer.addView(
                loanLayout
        );
    }

    // -----------------------------------------
    // LOAN APPLICATION DIALOG
    // -----------------------------------------

    private void showLoanApplicationDialog() {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setPadding(
                40,
                10,
                40,
                10
        );

        EditText amountInput =
                new EditText(this);

        amountInput.setHint(
                "Loan amount"
        );

        amountInput.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        EditText purposeInput =
                new EditText(this);

        purposeInput.setHint(
                "Loan purpose"
        );

        purposeInput.setInputType(
                InputType.TYPE_CLASS_TEXT
        );

        EditText typeInput =
                new EditText(this);

        typeInput.setHint(
                "Loan type e.g. Development"
        );

        typeInput.setInputType(
                InputType.TYPE_CLASS_TEXT
        );

        layout.addView(amountInput);
        layout.addView(typeInput);
        layout.addView(purposeInput);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Apply for a Loan"
                        )
                        .setMessage(
                                "Enter your loan details."
                        )
                        .setView(layout)
                        .setPositiveButton(
                                "SUBMIT",
                                null
                        )
                        .setNegativeButton(
                                "CANCEL",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                dialogInterface -> {

                    Button submitButton =
                            dialog.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            );

                    submitButton.setOnClickListener(
                            v -> {

                                String amountText =
                                        amountInput
                                                .getText()
                                                .toString()
                                                .trim();

                                String purpose =
                                        purposeInput
                                                .getText()
                                                .toString()
                                                .trim();

                                String loanType =
                                        typeInput
                                                .getText()
                                                .toString()
                                                .trim();

                                if (
                                        amountText.isEmpty()
                                ) {

                                    amountInput.setError(
                                            "Enter loan amount"
                                    );

                                    return;
                                }

                                if (
                                        purpose.isEmpty()
                                ) {

                                    purposeInput.setError(
                                            "Enter loan purpose"
                                    );

                                    return;
                                }

                                if (
                                        loanType.isEmpty()
                                ) {

                                    typeInput.setError(
                                            "Enter loan type"
                                    );

                                    return;
                                }

                                double amount;

                                try {

                                    amount =
                                            Double.parseDouble(
                                                    amountText
                                            );

                                } catch (
                                        NumberFormatException e
                                ) {

                                    amountInput.setError(
                                            "Enter a valid amount"
                                    );

                                    return;
                                }

                                if (amount <= 0) {

                                    amountInput.setError(
                                            "Amount must be greater than 0"
                                    );

                                    return;
                                }

                                submitLoanApplication(
                                        amount,
                                        loanType,
                                        purpose
                                );

                                dialog.dismiss();
                            }
                    );
                }
        );

        dialog.show();
    }

    // -----------------------------------------
    // SUBMIT LOAN
    // -----------------------------------------

    private void submitLoanApplication(
            double amount,
            String loanType,
            String purpose
    ) {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "Please login first",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String uid =
                currentUser.getUid();

        Map<String, Object> loan =
                new HashMap<>();

        loan.put(
                "userId",
                uid
        );

        loan.put(
                "amount",
                amount
        );

        loan.put(
                "loanType",
                loanType
        );

        loan.put(
                "purpose",
                purpose
        );

        // Important:
        // Members cannot approve their own loans.
        loan.put(
                "status",
                "PENDING"
        );

        loan.put(
                "timestamp",
                System.currentTimeMillis()
        );

        loan.put(
                "date",
                DateFormat
                        .getDateTimeInstance()
                        .format(
                                new Date()
                        )
        );

        firestore
                .collection("loans")
                .add(loan)
                .addOnSuccessListener(documentReference -> {

                    Toast.makeText(
                            LoanActivity.this,
                            "Loan application submitted successfully",
                            Toast.LENGTH_LONG
                    ).show();

                    loadLoans();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            LoanActivity.this,
                            "Loan application failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}