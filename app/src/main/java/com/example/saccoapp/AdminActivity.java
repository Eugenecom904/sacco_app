package com.example.saccoapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class AdminActivity extends AppCompatActivity {

    private LinearLayout pendingLoansContainer;
    private TextView tvNoPendingLoans;

    private Button btnRefreshLoans;
    private Button btnBack;

    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_admin);

        pendingLoansContainer =
                findViewById(R.id.pendingLoansContainer);

        tvNoPendingLoans =
                findViewById(R.id.tvNoPendingLoans);

        btnRefreshLoans =
                findViewById(R.id.btnRefreshLoans);

        btnBack =
                findViewById(R.id.btnBack);

        firestore =
                FirebaseFirestore.getInstance();

        loadPendingLoans();

        btnRefreshLoans.setOnClickListener(
                v -> loadPendingLoans()
        );

        btnBack.setOnClickListener(
                v -> finish()
        );
    }

    private void loadPendingLoans() {

        firestore
                .collection("loans")
                .whereEqualTo("status", "PENDING")

                .get()
                .addOnSuccessListener(querySnapshot -> {

                    pendingLoansContainer.removeAllViews();

                    if (querySnapshot.isEmpty()) {

                        pendingLoansContainer.addView(
                                tvNoPendingLoans
                        );

                        return;
                    }

                    for (
                            DocumentSnapshot document :
                            querySnapshot
                    ) {

                        addLoanToScreen(document);
                    }

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Unable to load loans: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void addLoanToScreen(
            DocumentSnapshot document
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

        TextView loanDetails =
                new TextView(this);

        Double amount =
                document.getDouble("amount");

        String loanType =
                document.getString("loanType");

        String purpose =
                document.getString("purpose");

        String date =
                document.getString("date");

        String userId =
                document.getString("userId");

        loanDetails.setText(
                "Loan Amount: KES "
                        + String.format(
                        "%.2f",
                        amount != null
                                ? amount
                                : 0.0
                )
                        + "\n\nLoan Type: "
                        + (loanType != null
                        ? loanType
                        : "N/A")
                        + "\n\nPurpose: "
                        + (purpose != null
                        ? purpose
                        : "N/A")
                        + "\n\nMember ID: "
                        + (userId != null
                        ? userId
                        : "N/A")
                        + "\n\nDate: "
                        + (date != null
                        ? date
                        : "N/A")
                        + "\n\nStatus: PENDING"
        );

        loanDetails.setTextSize(16);

        loanLayout.addView(
                loanDetails
        );

        LinearLayout buttonLayout =
                new LinearLayout(this);

        buttonLayout.setOrientation(
                LinearLayout.HORIZONTAL
        );

        Button approveButton =
                new Button(this);

        approveButton.setText(
                "APPROVE"
        );

        Button rejectButton =
                new Button(this);

        rejectButton.setText(
                "REJECT"
        );

        buttonLayout.addView(
                approveButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        buttonLayout.addView(
                rejectButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        loanLayout.addView(
                buttonLayout
        );

        pendingLoansContainer.addView(
                loanLayout
        );

        approveButton.setOnClickListener(
                v -> updateLoanStatus(
                        document.getId(),
                        "APPROVED"
                )
        );

        rejectButton.setOnClickListener(
                v -> updateLoanStatus(
                        document.getId(),
                        "REJECTED"
                )
        );
    }

    private void updateLoanStatus(
            String loanId,
            String newStatus
    ) {

        firestore
                .collection("loans")
                .document(loanId)
                .update(
                        "status",
                        newStatus
                )
                .addOnSuccessListener(
                        unused -> {

                            Toast.makeText(
                                    this,
                                    "Loan "
                                            + newStatus
                                            + " successfully",
                                    Toast.LENGTH_LONG
                            ).show();

                            loadPendingLoans();
                        }
                )
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Failed to update loan: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

}
