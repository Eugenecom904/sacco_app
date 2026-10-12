
require("dotenv").config();

const express = require("express");
const cors = require("cors");
const crypto = require("crypto");

const { initializeApp, cert } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const app = express();

app.use(cors());
app.use(express.json({ limit: "100kb" }));

const PORT = process.env.PORT || 3000;
const IS_SANDBOX = process.env.MPESA_ENV === "sandbox";

const DARAJA_BASE = IS_SANDBOX
  ? "https://sandbox.safaricom.co.ke"
  : "https://api.safaricom.co.ke";

if (!IS_SANDBOX) {
  throw new Error(
    "This backend is sandbox-only until live-payment verification is implemented."
  );
}

if (!process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
  throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON environment variable.");
}

if (!process.env.CALLBACK_TOKEN || process.env.CALLBACK_TOKEN.length < 32) {
  throw new Error("Set a strong CALLBACK_TOKEN in your environment.");
}

const serviceAccount = JSON.parse(
  process.env.FIREBASE_SERVICE_ACCOUNT_JSON
);

initializeApp({
  credential: cert(serviceAccount),
  projectId: process.env.FIREBASE_PROJECT_ID || "sacco-app-c48e2",
});

const db = getFirestore();

function normalizePhone(phone) {
  let value = String(phone || "").replace(/[\s+-]/g, "");

  if (/^0[17]\d{8}$/.test(value)) {
    value = "254" + value.substring(1);
  }

  if (!/^254[17]\d{8}$/.test(value)) {
    throw new Error("Enter a valid Kenyan M-Pesa phone number.");
  }

  return value;
}

function getTimestamp() {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Africa/Nairobi",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date());

  const values = {};

  for (const part of parts) {
    values[part.type] = part.value;
  }

  return (
    values.year +
    values.month +
    values.day +
    values.hour +
    values.minute +
    values.second
  );
}

async function getAccessToken() {
  const key = process.env.MPESA_CONSUMER_KEY;
  const secret = process.env.MPESA_CONSUMER_SECRET;

  if (!key || !secret || !process.env.MPESA_PASSKEY) {
    throw new Error("Daraja sandbox environment variables are incomplete.");
  }

  const credentials = Buffer.from(`${key}:${secret}`).toString("base64");

  const response = await fetch(
    `${DARAJA_BASE}/oauth/v1/generate?grant_type=client_credentials`,
    {
      headers: {
        Authorization: `Basic ${credentials}`,
      },
    }
  );

  const responseText = await response.text();

  let data = {};

  try {
    data = responseText ? JSON.parse(responseText) : {};
  } catch {
    data = { rawResponse: responseText };
  }

  if (!response.ok || !data.access_token) {
    console.error("Daraja OAuth HTTP status:", response.status);
    console.error(
      "Daraja OAuth error:",
      data.error_description || data.errorMessage || data.error || "Unknown error"
    );

    throw new Error(
      data.error_description ||
      data.errorMessage ||
      "Unable to obtain a Daraja access token."
    );
  }

  return data.access_token;
}

async function getOutstandingBalance(uid) {
  const [loanSnapshot, repaymentSnapshot] = await Promise.all([
    db.collection("loans")
      .where("userId", "==", uid)
      .where("status", "==", "APPROVED")
      .get(),

    db.collection("repayments")
      .where("userId", "==", uid)
      .get(),
  ]);

  let approvedLoans = 0;
  let paid = 0;

  loanSnapshot.forEach((doc) => {
    approvedLoans += Number(doc.data().amount || 0);
  });

  repaymentSnapshot.forEach((doc) => {
    const item = doc.data();

    if (!item.status || item.status === "SUCCESS") {
      paid += Number(item.amount || 0);
    }
  });

  return Math.max(0, approvedLoans - paid);
}

async function requireMember(req, res, next) {
  try {
    const header = req.headers.authorization || "";
    const match = header.match(/^Bearer (.+)$/);

    if (!match) {
      return res.status(401).json({
        error: "Sign in is required.",
      });
    }

    const decoded = await getAuth().verifyIdToken(match[1]);

    req.uid = decoded.uid;

    return next();
  } catch {
    return res.status(401).json({
      error: "Invalid or expired sign-in token.",
    });
  }
}

app.get("/", (_req, res) => {
  res.json({
    service: "SACCO M-Pesa sandbox backend",
    status: "running",
  });
});

app.post("/mpesa/stkpush", requireMember, async (req, res) => {
  console.log("STK Push route reached");

  let transactionRef;

  try {
    const amount = Number(req.body.amount);
    const phone = normalizePhone(req.body.phone);

    if (!Number.isSafeInteger(amount) || amount < 1 || amount > 150000) {
      return res.status(400).json({
        error: "Enter a whole-number amount between KES 1 and KES 150,000.",
      });
    }

    const balance = await getOutstandingBalance(req.uid);

    if (amount > balance) {
      return res.status(400).json({
        error: "The repayment exceeds your outstanding loan balance.",
      });
    }

    transactionRef = db.collection("mpesaTransactions").doc();

    await transactionRef.set({
      userId: req.uid,
      amount,
      phone,
      status: "PENDING",
      createdAt: FieldValue.serverTimestamp(),
    });

    const token = await getAccessToken();
    const timestamp = getTimestamp();

    const shortcode = process.env.MPESA_SHORTCODE || "174379";
    const passkey = process.env.MPESA_PASSKEY;

    const password = Buffer.from(
      shortcode + passkey + timestamp
    ).toString("base64");

    const baseUrl = (process.env.PUBLIC_BASE_URL || "").replace(/\/+$/, "");

    if (!baseUrl.startsWith("https://")) {
      throw new Error(
        "PUBLIC_BASE_URL must be the public HTTPS Render URL."
      );
    }

    const callbackToken = process.env.CALLBACK_TOKEN;

    if (!callbackToken) {
      throw new Error("CALLBACK_TOKEN is missing from environment variables.");
    }

    const callbackUrl =
      `${baseUrl}/mpesa/callback?token=${encodeURIComponent(callbackToken)}`;

    const response = await fetch(
      `${DARAJA_BASE}/mpesa/stkpush/v1/processrequest`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          BusinessShortCode: shortcode,
          Password: password,
          Timestamp: timestamp,
          TransactionType: "CustomerPayBillOnline",
          Amount: amount,
          PartyA: phone,
          PartyB: shortcode,
          PhoneNumber: phone,
          CallBackURL: callbackUrl,
          AccountReference: "SACCO",
          TransactionDesc: "Loan repayment",
        }),
      }
    );

    const responseText = await response.text();

    let data = {};

    try {
      data = responseText ? JSON.parse(responseText) : {};
    } catch {
      data = { rawResponse: responseText };
    }

    console.log("Daraja STK HTTP status:", response.status);
    console.log("Daraja STK response:", JSON.stringify(data));

    if (!response.ok || !data.CheckoutRequestID) {
      await transactionRef.update({
        status: "FAILED",
        updatedAt: FieldValue.serverTimestamp(),
      });

      const darajaMessage =
        data.errorMessage ||
        data.ResponseDescription ||
        data.ResponseDesc ||
        data.error ||
        "Safaricom did not accept the STK Push request.";

      return res.status(502).json({
        error: darajaMessage,
        darajaCode: data.errorCode || data.ResponseCode || null,
      });
    }

    await transactionRef.update({
      checkoutRequestId: data.CheckoutRequestID,
      merchantRequestId: data.MerchantRequestID || null,
      updatedAt: FieldValue.serverTimestamp(),
    });

    return res.status(200).json({
      success: true,
      message: data.CustomerMessage || "STK Push request accepted.",
      checkoutRequestId: data.CheckoutRequestID,
      merchantRequestId: data.MerchantRequestID || null,
      responseCode: data.ResponseCode || null,
    });
  } catch (error) {
    console.error("STK Push error:", error.message);

    if (transactionRef) {
      try {
        await transactionRef.update({
          status: "FAILED",
          errorMessage: error.message,
          updatedAt: FieldValue.serverTimestamp(),
        });
      } catch (updateError) {
        console.error(
          "Failed to update transaction:",
          updateError.message
        );
      }
    }

    return res.status(500).json({
      success: false,
      message: "Failed to initiate M-Pesa STK Push.",
    });
  }
});

app.post("/mpesa/callback", async (req, res) => {
  const provided = String(req.query.token || "");
  const expected = process.env.CALLBACK_TOKEN;

  if (
    !expected ||
    provided.length !== expected.length ||
    !crypto.timingSafeEqual(
      Buffer.from(provided),
      Buffer.from(expected)
    )
  ) {
    return res.status(403).send("Forbidden");
  }

  const callback = req.body?.Body?.stkCallback;

  if (!callback?.CheckoutRequestID) {
    return res.status(400).send("Invalid callback");
  }

  try {
    const matches = await db.collection("mpesaTransactions")
      .where("checkoutRequestId", "==", callback.CheckoutRequestID)
      .limit(1)
      .get();

    if (matches.empty) {
      console.warn("Callback received for an unknown checkout request.");
      return res.status(200).send("Received");
    }

    const transactionDoc = matches.docs[0];
    const transactionRef = transactionDoc.ref;
    const transaction = transactionDoc.data();

    if (transaction.status !== "PENDING") {
      return res.status(200).send("Already processed");
    }

    if (Number(callback.ResultCode) !== 0) {
      await transactionRef.update({
        status: "FAILED",
        resultCode: callback.ResultCode,
        resultDescription: callback.ResultDesc || "Payment failed",
        completedAt: FieldValue.serverTimestamp(),
      });

      return res.status(200).send("Received");
    }

    const metadata = {};

    for (const item of callback.CallbackMetadata?.Item || []) {
      metadata[item.Name] = item.Value;
    }

    const receipt = metadata.MpesaReceiptNumber;
    const callbackAmount = Number(metadata.Amount);
    const callbackPhone = String(metadata.PhoneNumber || "");

    if (
      !receipt ||
      callbackAmount !== transaction.amount ||
      (callbackPhone && callbackPhone !== transaction.phone)
    ) {
      await transactionRef.update({
        status: "REVIEW_REQUIRED",
        resultDescription: "Payment details require verification.",
      });

      return res.status(200).send("Received");
    }

    await db.runTransaction(async (tx) => {
      const current = await tx.get(transactionRef);

      if (!current.exists || current.data().status !== "PENDING") {
        return;
      }

      const repaymentRef = db.collection("repayments").doc(transactionDoc.id);

      tx.create(repaymentRef, {
        userId: transaction.userId,
        amount: transaction.amount,
        status: "SUCCESS",
        source: "MPESA_SANDBOX",
        receiptNumber: String(receipt),
        phone: transaction.phone,
        timestamp: Date.now(),
        createdAt: FieldValue.serverTimestamp(),
      });

      tx.update(transactionRef, {
        status: "SUCCESS",
        receiptNumber: String(receipt),
        completedAt: FieldValue.serverTimestamp(),
      });
    });

    return res.status(200).send("Received");
  } catch (error) {
    console.error("Callback processing failed:", error.message);
    return res.status(500).send("Processing failed");
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`SACCO M-Pesa backend listening on port ${PORT}`);
});