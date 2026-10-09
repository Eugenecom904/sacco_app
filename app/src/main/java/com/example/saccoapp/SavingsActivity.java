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

import java.util.HashMap;
import java.util.Map;

public class SavingsActivity extends AppCompatActivity {

    private TextView tvTotalSavings;
    private TextView tvSavingsMember;
    private TextView tvNoSavings;

    private LinearLayout savingsHistoryContainer;

    private Button btnDeposit;
    private Button btnBack;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private double totalSavings = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_savings);

        // Connect XML elements
        tvTotalSavings = findViewById(R.id.tvTotalSavings);
        tvSavingsMember = findViewById(R.id.tvSavingsMember);
        tvNoSavings = findViewById(R.id.tvNoSavings);

        savingsHistoryContainer =
                findViewById(R.id.savingsHistoryContainer);

        btnDeposit = findViewById(R.id.btnDeposit);
        btnBack = findViewById(R.id.btnBack);

        // Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Check logged-in user
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

        // Load member information
        loadMemberInformation();

        // Load savings
        loadSavings();

        // Deposit button
        btnDeposit.setOnClickListener(v ->
                showDepositDialog()
        );

        // Back button
        btnBack.setOnClickListener(v ->
                finish()
        );
    }

    // ---------------------------------------------------
    // LOAD MEMBER INFORMATION
    // ---------------------------------------------------

    private void loadMemberInformation() {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid = currentUser.getUid();

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

                            tvSavingsMember.setText(
                                    "Member Number: "
                                            + memberNumber
                            );
                        }
                    }
                });
    }

    // ---------------------------------------------------
    // LOAD SAVINGS
    // ---------------------------------------------------

    private void loadSavings() {

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid = currentUser.getUid();

        firestore
                .collection("savings")
                .whereEqualTo("userId", uid)
                .orderBy(
                        "timestamp",
                        Query.Direction.DESCENDING
                )
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    totalSavings = 0.0;

                    savingsHistoryContainer
                            .removeAllViews();

                    boolean hasTransactions =
                            !queryDocumentSnapshots.isEmpty();

                    if (!hasTransactions) {

                        savingsHistoryContainer
                                .addView(tvNoSavings);

                        tvTotalSavings.setText(
                                "KES 0.00"
                        );

                        return;
                    }

                    for (
                            DocumentSnapshot document :
                            queryDocumentSnapshots
                    ) {

                        Double amount =
                                document.getDouble("amount");

                        String type =
                                document.getString("type");

                        String date =
                                document.getString("date");

                        if (amount == null) {
                            amount = 0.0;
                        }

                        // Add deposits to total
                        if ("DEPOSIT".equals(type)) {

                            totalSavings += amount;
                        }

                        addTransactionToScreen(
                                type,
                                amount,
                                date
                        );
                    }

                    tvTotalSavings.setText(
                            String.format(
                                    "KES %.2f",
                                    totalSavings
                            )
                    );

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            SavingsActivity.this,
                            "Unable to load savings: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // ---------------------------------------------------
    // ADD TRANSACTION TO SCREEN
    // ---------------------------------------------------

    private void addTransactionToScreen(
            String type,
            double amount,
            String date
    ) {

        LinearLayout transactionLayout =
                new LinearLayout(this);

        transactionLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        transactionLayout.setPadding(
                20,
                20,
                20,
                20
        );

        TextView transactionType =
                new TextView(this);

        transactionType.setText(
                type
        );

        transactionType.setTextSize(17);

        transactionType.setTextColor(
                getResources()
                        .getColor(
                                android.R.color.black
                        )
        );

        TextView transactionAmount =
                new TextView(this);

        transactionAmount.setText(
                String.format(
                        "KES %.2f",
                        amount
                )
        );

        transactionAmount.setTextSize(18);

        transactionAmount.setTextColor(
                getResources()
                        .getColor(
                                android.R.color.holo_green_dark
                        )
        );

        TextView transactionDate =
                new TextView(this);

        transactionDate.setText(
                date != null
                        ? date
                        : ""
        );

        transactionDate.setTextSize(13);

        transactionDate.setTextColor(
                getResources()
                        .getColor(
                                android.R.color.darker_gray
                        )
        );

        transactionLayout.addView(
                transactionType
        );

        transactionLayout.addView(
                transactionAmount
        );

        transactionLayout.addView(
                transactionDate
        );

        savingsHistoryContainer.addView(
                transactionLayout
        );
    }

    // ---------------------------------------------------
    // DEPOSIT DIALOG
    // ---------------------------------------------------

    private void showDepositDialog() {

        final EditText input =
                new EditText(this);

        input.setHint(
                "Enter amount"
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
                                "Make Savings Deposit"
                        )
                        .setMessage(
                                "Enter the amount you want to save."
                        )
                        .setView(layout)
                        .setPositiveButton(
                                "DEPOSIT",
                                null
                        )
                        .setNegativeButton(
                                "CANCEL",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                dialogInterface -> {

                    Button depositButton =
                            dialog.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            );

                    depositButton.setOnClickListener(
                            v -> {

                                String amountText =
                                        input.getText()
                                                .toString()
                                                .trim();

                                if (
                                        amountText.isEmpty()
                                ) {

                                    input.setError(
                                            "Enter an amount"
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

                                if (amount <= 0) {

                                    input.setError(
                                            "Amount must be greater than 0"
                                    );

                                    return;
                                }

                                saveDeposit(amount);

                                dialog.dismiss();
                            }
                    );
                }
        );

        dialog.show();
    }

    // ---------------------------------------------------
    // SAVE DEPOSIT TO FIRESTORE
    // ---------------------------------------------------

    private void saveDeposit(double amount) {

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

        Map<String, Object> savings =
                new HashMap<>();

        savings.put(
                "userId",
                uid
        );

        savings.put(
                "amount",
                amount
        );

        savings.put(
                "type",
                "DEPOSIT"
        );

        savings.put(
                "timestamp",
                System.currentTimeMillis()
        );

        savings.put(
                "date",
                java.text.DateFormat
                        .getDateTimeInstance()
                        .format(
                                new java.util.Date()
                        )
        );

        firestore
                .collection("savings")
                .add(savings)
                .addOnSuccessListener(documentReference -> {

                    Toast.makeText(
                            SavingsActivity.this,
                            "Deposit recorded successfully",
                            Toast.LENGTH_LONG
                    ).show();

                    // Reload savings
                    loadSavings();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            SavingsActivity.this,
                            "Deposit failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}