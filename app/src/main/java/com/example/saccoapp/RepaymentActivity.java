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

public class RepaymentActivity extends AppCompatActivity {

    private TextView tvRepaymentMember;
    private TextView tvOutstandingBalance;
    private TextView tvNoRepayments;

    private LinearLayout repaymentHistoryContainer;

    private Button btnMakeRepayment;
    private Button btnBack;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private double approvedLoans = 0.0;
    private double totalRepayments = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_repayment);

        tvRepaymentMember =
                findViewById(R.id.tvRepaymentMember);

        tvOutstandingBalance =
                findViewById(R.id.tvOutstandingBalance);

        tvNoRepayments =
                findViewById(R.id.tvNoRepayments);

        repaymentHistoryContainer =
                findViewById(
                        R.id.repaymentHistoryContainer
                );

        btnMakeRepayment =
                findViewById(R.id.btnMakeRepayment);

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

        loadLoanBalance();

        loadRepayments();

        btnMakeRepayment.setOnClickListener(
                v -> showRepaymentDialog()
        );

        btnBack.setOnClickListener(
                v -> finish()
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

                            tvRepaymentMember.setText(
                                    "Member Number: "
                                            + memberNumber
                            );
                        }
                    }
                });
    }

    // -----------------------------------------
    // LOAD APPROVED LOANS
    // -----------------------------------------

    private void loadLoanBalance() {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid =
                currentUser.getUid();

        firestore
                .collection("loans")
                .whereEqualTo(
                        "userId",
                        uid
                )
                .whereEqualTo(
                        "status",
                        "APPROVED"
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            approvedLoans = 0.0;

                            for (
                                    DocumentSnapshot document :
                                    querySnapshot
                            ) {

                                Double amount =
                                        document.getDouble(
                                                "amount"
                                        );

                                if (amount != null) {

                                    approvedLoans +=
                                            amount;
                                }
                            }

                            calculateOutstandingBalance();
                        }
                )
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to load loan balance: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // -----------------------------------------
    // LOAD REPAYMENTS
    // -----------------------------------------

    private void loadRepayments() {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid =
                currentUser.getUid();

        firestore
                .collection("repayments")
                .whereEqualTo(
                        "userId",
                        uid
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            totalRepayments = 0.0;

                            repaymentHistoryContainer
                                    .removeAllViews();

                            if (
                                    querySnapshot.isEmpty()
                            ) {

                                repaymentHistoryContainer
                                        .addView(
                                                tvNoRepayments
                                        );

                                calculateOutstandingBalance();

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

                                String date =
                                        document.getString(
                                                "date"
                                        );

                                if (amount == null) {
                                    amount = 0.0;
                                }

                                totalRepayments +=
                                        amount;

                                addRepaymentToScreen(
                                        amount,
                                        date
                                );
                            }

                            calculateOutstandingBalance();
                        }
                )
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to load repayments: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // -----------------------------------------
    // CALCULATE BALANCE
    // -----------------------------------------

    private void calculateOutstandingBalance() {

        double balance =
                approvedLoans -
                        totalRepayments;

        if (balance < 0) {
            balance = 0;
        }

        tvOutstandingBalance.setText(
                String.format(
                        "KES %.2f",
                        balance
                )
        );

        if (balance <= 0) {

            btnMakeRepayment.setEnabled(
                    false
            );

        } else {

            btnMakeRepayment.setEnabled(
                    true
            );
        }
    }

    // -----------------------------------------
    // REPAYMENT DIALOG
    // -----------------------------------------

    private void showRepaymentDialog() {

        final EditText input =
                new EditText(this);

        input.setHint(
                "Enter repayment amount"
        );

        input.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        LinearLayout layout =
                new LinearLayout(this);

        layout.setPadding(
                50,
                10,
                50,
                10
        );

        layout.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Make Loan Repayment"
                        )
                        .setMessage(
                                "Enter the amount you want to repay."
                        )
                        .setView(layout)
                        .setPositiveButton(
                                "REPAY",
                                null
                        )
                        .setNegativeButton(
                                "CANCEL",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                dialogInterface -> {

                    Button repayButton =
                            dialog.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            );

                    repayButton.setOnClickListener(
                            v -> {

                                String amountText =
                                        input.getText()
                                                .toString()
                                                .trim();

                                if (
                                        amountText.isEmpty()
                                ) {

                                    input.setError(
                                            "Enter repayment amount"
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

                                    input.setError(
                                            "Enter a valid amount"
                                    );

                                    return;
                                }

                                double balance =
                                        approvedLoans -
                                                totalRepayments;

                                if (amount <= 0) {

                                    input.setError(
                                            "Amount must be greater than 0"
                                    );

                                    return;
                                }

                                if (amount > balance) {

                                    input.setError(
                                            "Amount cannot exceed outstanding balance"
                                    );

                                    return;
                                }

                                saveRepayment(
                                        amount
                                );

                                dialog.dismiss();
                            }
                    );
                }
        );

        dialog.show();
    }

    // -----------------------------------------
    // SAVE REPAYMENT
    // -----------------------------------------

    private void saveRepayment(
            double amount
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

        Map<String, Object> repayment =
                new HashMap<>();

        repayment.put(
                "userId",
                uid
        );

        repayment.put(
                "amount",
                amount
        );

        repayment.put(
                "timestamp",
                System.currentTimeMillis()
        );

        repayment.put(
                "date",
                DateFormat
                        .getDateTimeInstance()
                        .format(
                                new Date()
                        )
        );

        firestore
                .collection("repayments")
                .add(repayment)
                .addOnSuccessListener(
                        documentReference -> {

                            Toast.makeText(
                                    this,
                                    "Repayment recorded successfully",
                                    Toast.LENGTH_LONG
                            ).show();

                            loadRepayments();
                            loadLoanBalance();
                        }
                )
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Repayment failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // -----------------------------------------
    // DISPLAY REPAYMENT
    // -----------------------------------------

    private void addRepaymentToScreen(
            double amount,
            String date
    ) {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setPadding(
                20,
                20,
                20,
                20
        );

        TextView amountText =
                new TextView(this);

        amountText.setText(
                String.format(
                        "Repayment: KES %.2f",
                        amount
                )
        );

        amountText.setTextSize(18);

        TextView dateText =
                new TextView(this);

        dateText.setText(
                "Date: " +
                        (date != null
                                ? date
                                : "")
        );

        dateText.setTextSize(14);

        layout.addView(
                amountText
        );

        layout.addView(
                dateText
        );

        repaymentHistoryContainer.addView(
                layout
        );
    }
}