
require("dotenv").config();

const express = require("express");

const app = express();
app.use(express.json({ limit: "100kb" }));

const PORT = process.env.PORT || 3000;
const ENVIRONMENT = (process.env.DARAJA_ENV || "sandbox").toLowerCase();

const CONSUMER_KEY = process.env.DARAJA_CONSUMER_KEY;
const CONSUMER_SECRET = process.env.DARAJA_CONSUMER_SECRET;
const SHORTCODE = process.env.DARAJA_SHORTCODE;
const PASSKEY = process.env.DARAJA_PASSKEY;
const CALLBACK_URL = process.env.DARAJA_CALLBACK_URL;

const API_BASE =
  ENVIRONMENT === "production"
    ? "https://api.safaricom.co.ke"
    : "https://sandbox.safaricom.co.ke";

// Temporary storage. Transactions will be lost if this server restarts.
const payments = new Map();

function safeMessage(error) {
  return error?.response?.data?.errorMessage ||
    error?.message ||
    "An unexpected server error occurred.";
}

function normalizePhone(phone) {
  let value = String(phone || "").replace(/[\s+\-()]/g, "");

  if (value.startsWith("0")) {
    value = "254" + value.substring(1);
  } else if (value.startsWith("7") || value.startsWith("1")) {
    value = "254" + value;
  }

  if (!/^254[17]\d{8}$/.test(value)) {
    throw new Error("Enter a valid Kenyan mobile number.");
  }

  return value;
}

function getTimestamp() {
  const date = new Date();
  const pad = (number) => String(number).padStart(2, "0");

  return (
    date.getFullYear() +
    pad(date.getMonth() + 1) +
    pad(date.getDate()) +
    pad(date.getHours()) +
    pad(date.getMinutes()) +
    pad(date.getSeconds())
  );
}

async function getAccessToken() {
  if (!CONSUMER_KEY || !CONSUMER_SECRET) {
    throw new Error("Daraja consumer credentials are missing.");
  }

  const credentials = Buffer.from(
    `${CONSUMER_KEY}:${CONSUMER_SECRET}`
  ).toString("base64");

  const response = await fetch(
    `${API_BASE}/oauth/v1/generate?grant_type=client_credentials`,
    {
      method: "GET",
      headers: {
        Authorization: `Basic ${credentials}`
      },
      signal: AbortSignal.timeout(20000)
    }
  );

  const data = await response.json();

  if (!response.ok || !data.access_token) {
    console.error("Daraja token request failed:", data);
    throw new Error("Could not obtain a Daraja access token.");
  }

  return data.access_token;
}

function normalizeMetadata(items) {
  const result = {};

  for (const item of items || []) {
    if (item.Name) {
      result[item.Name] = item.Value;
    }
  }

  return result;
}

function samePhone(first, second) {
  try {
    return normalizePhone(first) === normalizePhone(second);
  } catch {
    return false;
  }
}

// Health check
app.get("/", (req, res) => {
  res.json({
    success: true,
    message: "E-commerce M-PESA backend is running.",
    environment: ENVIRONMENT
  });
});

// Initiate an M-PESA STK Push
app.post("/api/mpesa/stkpush", async (req, res) => {
  try {
    const phone = normalizePhone(req.body.phone);
    const amount = Number(req.body.amount);

    if (!Number.isSafeInteger(amount) || amount < 1) {
      return res.status(400).json({
        success: false,
        message: "Amount must be a whole number greater than zero."
      });
    }

    if (!SHORTCODE || !PASSKEY || !CALLBACK_URL) {
      return res.status(500).json({
        success: false,
        message: "Daraja shortcode, passkey, or callback URL is missing."
      });
    }

    const token = await getAccessToken();
    const timestamp = getTimestamp();

    const password = Buffer.from(
      `${SHORTCODE}${PASSKEY}${timestamp}`
    ).toString("base64");

    const payload = {
      BusinessShortCode: SHORTCODE,
      Password: password,
      Timestamp: timestamp,
      TransactionType: "CustomerPayBillOnline",
      Amount: amount,
      PartyA: phone,
      PartyB: SHORTCODE,
      PhoneNumber: phone,
      CallBackURL: CALLBACK_URL,
      AccountReference: "ECOMMERCE",
      TransactionDesc: "E-commerce order payment"
    };

    const response = await fetch(
      `${API_BASE}/mpesa/stkpush/v1/processrequest`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(30000)
      }
    );

    const data = await response.json();

    console.log("Daraja STK response:", JSON.stringify(data));

    if (
      !response.ok ||
      data.ResponseCode !== "0" ||
      !data.CheckoutRequestID
    ) {
      return res.status(502).json({
        success: false,
        message: data.ResponseDescription ||
          data.errorMessage ||
          "M-PESA could not initiate the payment.",
        details: data
      });
    }

    const checkoutRequestId = data.CheckoutRequestID;

    payments.set(checkoutRequestId, {
      checkoutRequestId,
      merchantRequestId: data.MerchantRequestID || null,
      phone,
      amount,
      status: "PENDING",
      callbackReceived: false,
      createdAt: new Date().toISOString(),
      receiptNumber: null,
      resultDescription: data.ResponseDescription || "STK Push accepted."
    });

    return res.json({
      success: true,
      message: data.CustomerMessage ||
        "STK Push sent. Check your phone.",
      checkoutRequestId,
      merchantRequestId: data.MerchantRequestID || null,
      status: "PENDING"
    });
  } catch (error) {
    console.error("STK Push error:", safeMessage(error));

    return res.status(500).json({
      success: false,
      message: safeMessage(error)
    });
  }
});

// Receive the Daraja callback
app.post("/api/payment/callback", (req, res) => {
  // Acknowledge the callback to Safaricom promptly.
  res.status(200).json({
    ResultCode: 0,
    ResultDesc: "Callback received"
  });

  try {
    const callback = req.body?.Body?.stkCallback;

    if (!callback) {
      console.error("Callback received with an unexpected body format.");
      return;
    }

    const checkoutId = callback.CheckoutRequestID;
    const payment = payments.get(checkoutId);

    console.log("M-PESA callback received:", JSON.stringify({
      checkoutRequestId: checkoutId,
      merchantRequestId: callback.MerchantRequestID,
      resultCode: callback.ResultCode,
      resultDescription: callback.ResultDesc,
      metadataFields: (callback.CallbackMetadata?.Item || [])
        .map((item) => item.Name)
    }));

    if (!payment) {
      console.error("Callback transaction not found in memory:", checkoutId);
      return;
    }

    // Ignore duplicate callbacks after a final result has been recorded.
    if (payment.callbackReceived) {
      console.log("Duplicate callback ignored:", checkoutId);
      return;
    }

    if (
      payment.merchantRequestId &&
      callback.MerchantRequestID &&
      payment.merchantRequestId !== callback.MerchantRequestID
    ) {
      payment.status = "REVIEW_REQUIRED";
      payment.callbackReceived = true;
      payment.resultDescription = "Merchant request ID mismatch.";
      console.error("Payment requires manual review:", checkoutId);
      return;
    }

    payment.callbackReceived = true;
    payment.resultDescription =
      callback.ResultDesc || "Callback processed.";

    if (Number(callback.ResultCode) !== 0) {
      payment.status = "FAILED";
      console.log("Payment failed:", checkoutId);
      return;
    }

    const metadata = normalizeMetadata(
      callback.CallbackMetadata?.Item
    );

    const receivedAmount = Number(metadata.Amount);
    const receivedPhone = String(metadata.PhoneNumber || "");
    const receiptNumber = String(
      metadata.MpesaReceiptNumber || ""
    ).trim();

    // A success result without matching payment details is not confirmed.
    const amountMatches =
      Number.isFinite(receivedAmount) &&
      receivedAmount === payment.amount;

    const phoneMatches =
      samePhone(receivedPhone, payment.phone);

    if (!amountMatches || !phoneMatches || !receiptNumber) {
      payment.status = "REVIEW_REQUIRED";
      payment.resultDescription =
        "Payment details are missing or do not match the request.";

      console.error("Payment requires manual review:", JSON.stringify({
        checkoutRequestId: checkoutId,
        amountMatches,
        phoneMatches,
        receiptPresent: Boolean(receiptNumber)
      }));

      return;
    }

    payment.status = "SUCCESS";
    payment.receiptNumber = receiptNumber;
    payment.paidAt = new Date().toISOString();

    console.log("Payment confirmed:", JSON.stringify({
      checkoutRequestId: checkoutId,
      receiptNumber,
      amount: payment.amount
    }));
  } catch (error) {
    console.error("Callback processing error:", safeMessage(error));
  }
});

// Check the status of an STK Push transaction
app.get("/api/mpesa/status/:id", (req, res) => {
  const checkoutId = req.params.id;
  const payment = payments.get(checkoutId);

  if (!payment) {
    return res.status(404).json({
      success: false,
      checkoutRequestId: checkoutId,
      status: "NOT_FOUND",
      message:
        "Transaction was not found. Do not assume payment failed; verify it before retrying."
    });
  }

  return res.json({
    success: true,
    checkoutRequestId: payment.checkoutRequestId,
    status: payment.status,
    amount: payment.amount,
    receiptNumber: payment.receiptNumber,
    resultDescription: payment.resultDescription,
    validationError:
      payment.status === "REVIEW_REQUIRED"
        ? payment.resultDescription
        : null,
    paidAt: payment.paidAt || null
  });
});

// Unknown routes
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: "Endpoint not found."
  });
});

// General error handler
app.use((error, req, res, next) => {
  console.error("Server error:", safeMessage(error));

  if (res.headersSent) {
    return next(error);
  }

  res.status(500).json({
    success: false,
    message: "An internal server error occurred."
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`E-commerce M-PESA backend listening on port ${PORT}`);
  console.log(`Daraja environment: ${ENVIRONMENT}`);
});
