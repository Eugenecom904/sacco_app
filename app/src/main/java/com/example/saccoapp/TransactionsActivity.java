package com.example.saccoapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionsActivity extends AppCompatActivity {

    private TextView tvTransactionMember;
    private TextView tvTransactionCount;
    private TextView tvNoTransactions;

    private LinearLayout transactionHistoryContainer;

    private Button btnRefreshTransactions;
    private Button btnBack;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private List<TransactionItem> transactions =
            new ArrayList<>();

    // Keeps track of Firestore documents already added
    private Set<String> transactionIds =
            new HashSet<>();

    // Prevents multiple loading operations at the same time
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_transactions
        );

        tvTransactionMember =
                findViewById(
                        R.id.tvTransactionMember
                );

        tvTransactionCount =
                findViewById(
                        R.id.tvTransactionCount
                );

        tvNoTransactions =
                findViewById(
                        R.id.tvNoTransactions
                );

        transactionHistoryContainer =
                findViewById(
                        R.id.transactionHistoryContainer
                );

        btnRefreshTransactions =
                findViewById(
                        R.id.btnRefreshTransactions
                );

        btnBack =
                findViewById(
                        R.id.btnBack
                );

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

        loadTransactions();

        btnRefreshTransactions.setOnClickListener(
                v -> loadTransactions()
        );

        btnBack.setOnClickListener(
                v -> finish()
        );
    }

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
                .addOnSuccessListener(
                        documentSnapshot -> {

                            if (
                                    documentSnapshot.exists()
                            ) {

                                String memberNumber =
                                        documentSnapshot
                                                .getString(
                                                        "memberNumber"
                                                );

                                if (
                                        memberNumber != null
                                ) {

                                    tvTransactionMember
                                            .setText(
                                                    "Member Number: "
                                                            + memberNumber
                                            );
                                }
                            }
                        }
                );
    }

    private void loadTransactions() {

        // Prevent another loading operation
        // from starting before the current one finishes.
        if (isLoading) {
            return;
        }

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid =
                currentUser.getUid();

        isLoading = true;

        // Disable refresh while loading
        btnRefreshTransactions.setEnabled(false);

        // Clear old data
        transactions.clear();

        transactionIds.clear();

        transactionHistoryContainer
                .removeAllViews();

        tvTransactionCount.setText(
                "Loading transactions..."
        );

        loadSavingsTransactions(uid);
    }

    private void loadSavingsTransactions(
            String uid
    ) {

        firestore
                .collection("savings")
                .whereEqualTo(
                        "userId",
                        uid
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            for (
                                    DocumentSnapshot document :
                                    querySnapshot
                            ) {

                                String documentId =
                                        "savings_"
                                                + document.getId();

                                // Prevent duplicate Firestore document
                                if (
                                        transactionIds
                                                .contains(
                                                        documentId
                                                )
                                ) {
                                    continue;
                                }

                                Double amount =
                                        document.getDouble(
                                                "amount"
                                        );

                                String date =
                                        document.getString(
                                                "date"
                                        );

                                Long timestamp =
                                        document.getLong(
                                                "timestamp"
                                        );

                                if (amount == null) {
                                    amount = 0.0;
                                }

                                if (timestamp == null) {
                                    timestamp = 0L;
                                }

                                transactionIds.add(
                                        documentId
                                );

                                transactions.add(
                                        new TransactionItem(
                                                "Savings Deposit",
                                                amount,
                                                date,
                                                timestamp
                                        )
                                );
                            }

                            loadLoanTransactions(uid);
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to load savings transactions: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();

                            loadLoanTransactions(uid);
                        }
                );
    }

    private void loadLoanTransactions(
            String uid
    ) {

        firestore
                .collection("loans")
                .whereEqualTo(
                        "userId",
                        uid
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            for (
                                    DocumentSnapshot document :
                                    querySnapshot
                            ) {

                                String status =
                                        document.getString(
                                                "status"
                                        );

                                // Only approved loans
                                // are financial transactions.
                                if (
                                        !"APPROVED".equals(
                                                status
                                        )
                                ) {
                                    continue;
                                }

                                String documentId =
                                        "loan_"
                                                + document.getId();

                                // Prevent duplicate document
                                if (
                                        transactionIds
                                                .contains(
                                                        documentId
                                                )
                                ) {
                                    continue;
                                }

                                Double amount =
                                        document.getDouble(
                                                "amount"
                                        );

                                String date =
                                        document.getString(
                                                "date"
                                        );

                                Long timestamp =
                                        document.getLong(
                                                "timestamp"
                                        );

                                if (amount == null) {
                                    amount = 0.0;
                                }

                                if (timestamp == null) {
                                    timestamp = 0L;
                                }

                                transactionIds.add(
                                        documentId
                                );

                                transactions.add(
                                        new TransactionItem(
                                                "Loan Approved",
                                                amount,
                                                date,
                                                timestamp
                                        )
                                );
                            }

                            loadRepaymentTransactions(uid);
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to load loan transactions: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();

                            loadRepaymentTransactions(uid);
                        }
                );
    }

    private void loadRepaymentTransactions(
            String uid
    ) {

        firestore
                .collection("repayments")
                .whereEqualTo(
                        "userId",
                        uid
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            for (
                                    DocumentSnapshot document :
                                    querySnapshot
                            ) {

                                String documentId =
                                        "repayment_"
                                                + document.getId();

                                // Prevent duplicate document
                                if (
                                        transactionIds
                                                .contains(
                                                        documentId
                                                )
                                ) {
                                    continue;
                                }

                                Double amount =
                                        document.getDouble(
                                                "amount"
                                        );

                                String date =
                                        document.getString(
                                                "date"
                                        );

                                Long timestamp =
                                        document.getLong(
                                                "timestamp"
                                        );

                                if (amount == null) {
                                    amount = 0.0;
                                }

                                if (timestamp == null) {
                                    timestamp = 0L;
                                }

                                transactionIds.add(
                                        documentId
                                );

                                transactions.add(
                                        new TransactionItem(
                                                "Loan Repayment",
                                                amount,
                                                date,
                                                timestamp
                                        )
                                );
                            }

                            displayTransactions();
                        }
                )
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    this,
                                    "Unable to load repayment transactions: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();

                            displayTransactions();
                        }
                );
    }

    private void displayTransactions() {

        transactionHistoryContainer
                .removeAllViews();

        Collections.sort(
                transactions,
                new Comparator<TransactionItem>() {

                    @Override
                    public int compare(
                            TransactionItem first,
                            TransactionItem second
                    ) {

                        return Long.compare(
                                second.timestamp,
                                first.timestamp
                        );
                    }
                }
        );

        if (transactions.isEmpty()) {

            transactionHistoryContainer
                    .addView(
                            tvNoTransactions
                    );

            tvTransactionCount.setText(
                    "0 Transactions"
            );

        } else {

            tvTransactionCount.setText(
                    transactions.size()
                            + " Transactions"
            );

            for (
                    TransactionItem transaction :
                    transactions
            ) {

                addTransactionToScreen(
                        transaction
                );
            }
        }

        // Loading is now complete
        isLoading = false;

        // Allow refresh again
        btnRefreshTransactions.setEnabled(true);
    }

    private void addTransactionToScreen(
            TransactionItem transaction
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

        TextView typeText =
                new TextView(this);

        typeText.setText(
                transaction.type
        );

        typeText.setTextSize(18);

        TextView amountText =
                new TextView(this);

        amountText.setText(
                String.format(
                        "KES %.2f",
                        transaction.amount
                )
        );

        amountText.setTextSize(17);

        TextView dateText =
                new TextView(this);

        dateText.setText(
                "Date: "
                        + (
                        transaction.date != null
                                ? transaction.date
                                : "N/A"
                )
        );

        dateText.setTextSize(14);

        layout.addView(
                typeText
        );

        layout.addView(
                amountText
        );

        layout.addView(
                dateText
        );

        transactionHistoryContainer
                .addView(
                        layout
                );
    }

    private static class TransactionItem {

        String type;
        double amount;
        String date;
        long timestamp;

        TransactionItem(
                String type,
                double amount,
                String date,
                long timestamp
        ) {

            this.type =
                    type;

            this.amount =
                    amount;

            this.date =
                    date;

            this.timestamp =
                    timestamp;
        }
    }
}
