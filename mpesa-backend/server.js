
require("dotenv").config();

const express = require("express");
const cors = require("cors");
const rateLimit = require("express-rate-limit");

const app = express();

app.set("trust proxy", 1);

const PORT = process.env.PORT || 3000;
const payments = new Map();

app.use(express.json({ limit: "100kb" }));

// Restrict browser access when FRONTEND_ORIGIN is configured.
// Android native HTTP requests do not rely on browser CORS.
const corsOptions = process.env.FRONTEND_ORIGIN
  ? { origin: process.env.FRONTEND_ORIGIN }
  : { origin: false };

app.use(cors(corsOptions));

app.use((req, res, next) => {
  res.setHeader("Cache-Control", "no-store");
  next();
});

const stkLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: true,
  legacyHeaders: false
});

const statusLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 60,
  standardHeaders: true,
  legacyHeaders: false
});

function config() {
  const required = [
    "DARAJA_CONSUMER_KEY",
    "DARAJA_CONSUMER_SECRET",
    "DARAJA_SHORTCODE",
    "DARAJA_PASSKEY",
    "DARAJA_CALLBACK_URL"
  ];

  const missing = required.filter(
    key => !process.env[key]?.trim()
  );

  if (missing.length) {
    throw new Error(
      "Missing environment variables: " + missing.join(", ")
    );
  }

  const callback = process.env.DARAJA_CALLBACK_URL.trim();

  if (!callback.startsWith("https://")) {
    throw new Error("DARAJA_CALLBACK_URL must use HTTPS.");
  }

  const environment = (
    process.env.DARAJA_ENV || "sandbox"
  ).toLowerCase();

  if (!["sandbox", "production"].includes(environment)) {
    throw new Error(
      "DARAJA_ENV must be sandbox or production."
    );
  }

  return {
    key: process.env.DARAJA_CONSUMER_KEY.trim(),
    secret: process.env.DARAJA_CONSUMER_SECRET.trim(),
    shortcode: process.env.DARAJA_SHORTCODE.trim(),
    passkey: process.env.DARAJA_PASSKEY.trim(),
    callback,
    environment
  };
}

function baseUrl() {
  return config().environment === "production"
    ? "https://api.safaricom.co.ke"
    : "https://sandbox.safaricom.co.ke";
}

function phoneNumber(value) {
  let phone = String(value || "")
    .trim()
    .replace(/[\s+()-]/g, "");

  if (phone.startsWith("0")) {
    phone = "254" + phone.slice(1);
  } else if (/^[17]\d{8}$/.test(phone)) {
    phone = "254" + phone;
  }

  if (!/^254[17]\d{8}$/.test(phone)) {
    throw new Error(
      "Enter a valid Kenyan phone number, e.g. 0712345678."
    );
  }

  return phone;
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
    hourCycle: "h23"
  }).formatToParts(new Date());

  const values = {};

  for (const part of parts) {
    if (part.type !== "literal") {
      values[part.type] = part.value;
    }
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

async function readDarajaResponse(response) {
  const raw = await response.text();

  let result;

  try {
    result = JSON.parse(raw);
  } catch {
    throw new Error(
      "Daraja returned an unreadable response (HTTP " +
      response.status + ")."
    );
  }

  if (!response.ok) {
    console.error("Daraja HTTP error:", response.status);

    throw new Error(
      result.errorMessage ||
      result.error_description ||
      result.ResponseDescription ||
      "Daraja request failed."
    );
  }

  return result;
}

async function getToken() {
  const credentials = config();

  const basic = Buffer.from(
    credentials.key + ":" + credentials.secret
  ).toString("base64");

  const response = await fetch(
    baseUrl() +
      "/oauth/v1/generate?grant_type=client_credentials",
    {
      headers: {
        Authorization: "Basic " + basic
      },
      signal: AbortSignal.timeout(30000)
    }
  );

  const result = await readDarajaResponse(response);

  if (!result.access_token) {
    throw new Error("Daraja did not return an access token.");
  }

  return result.access_token;
}

async function darajaPost(path, body, token, timeout = 30000) {
  const response = await fetch(baseUrl() + path, {
    method: "POST",
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeout)
  });

  return readDarajaResponse(response);
}

function getPassword(credentials, timestamp) {
  return Buffer.from(
    credentials.shortcode +
    credentials.passkey +
    timestamp
  ).toString("base64");
}

function metadataObject(items) {
  const result = {};

  for (const item of items || []) {
    if (item && typeof item.Name === "string") {
      result[item.Name] = item.Value;
    }
  }

  return result;
}

function safeMessage(error) {
  if (
    error.name === "TimeoutError" ||
    error.name === "AbortError"
  ) {
    return "The Daraja request timed out. Check payment status before retrying.";
  }

  return error.message || "The payment request failed.";
}

// Root endpoint.
app.get("/", (req, res) => {
  res.json({
    success: true,
    message: "E-commerce M-PESA backend is running.",
    environment: config().environment
  });
});

// Health check.
app.get("/health", (req, res) => {
  res.json({
    success: true,
    status: "healthy"
  });
});

// Initiate STK Push.
app.post("/api/mpesa/stkpush", stkLimiter, async (req, res) => {
  try {
    const credentials = config();

    const phone = phoneNumber(req.body?.phone);
    const amount = Number(req.body?.amount);

    if (
      !Number.isSafeInteger(amount) ||
      amount < 1 ||
      amount > 10000
    ) {
      return res.status(400).json({
        success: false,
        message: "Amount must be a whole number from 1 to 10000."
      });
    }

    const token = await getToken();
    const timestamp = getTimestamp();
    const password = getPassword(credentials, timestamp);

    const result = await darajaPost(
      "/mpesa/stkpush/v1/processrequest",
      {
        BusinessShortCode: credentials.shortcode,
        Password: password,
        Timestamp: timestamp,
        TransactionType: "CustomerPayBillOnline",
        Amount: amount,
        PartyA: phone,
        PartyB: credentials.shortcode,
        PhoneNumber: phone,
        CallBackURL: credentials.callback,
        AccountReference: "ECOMMERCE",
        TransactionDesc: "E-commerce order payment"
      },
      token,
      45000
    );

    console.log("STK response:", JSON.stringify({
      responseCode: result.ResponseCode,
      checkoutRequestId: result.CheckoutRequestID,
      responseDescription: result.ResponseDescription
    }));

    if (
      String(result.ResponseCode) !== "0" ||
      !result.CheckoutRequestID
    ) {
      return res.status(502).json({
        success: false,
        message:
          result.errorMessage ||
          result.ResponseDescription ||
          "Daraja rejected the STK Push request."
      });
    }

    const checkoutId = result.CheckoutRequestID;

    payments.set(checkoutId, {
      checkoutRequestId: checkoutId,
      merchantRequestId: result.MerchantRequestID || null,
      phone,
      amount,
      status: "PENDING",
      callbackReceived: false,
      createdAt: new Date().toISOString()
    });

    return res.status(200).json({
      success: true,
      message: "STK Push accepted. Check your phone.",
      checkoutRequestId: checkoutId,
      merchantRequestId: result.MerchantRequestID || null,
      status: "PENDING"
    });
  } catch (error) {
    console.error("STK Push error:", error.message);

    const timeout =
      error.name === "TimeoutError" ||
      error.name === "AbortError";

    return res.status(timeout ? 504 : 502).json({
      success: false,
      message: safeMessage(error)
    });
  }
});

// Receive the Daraja callback.
app.post("/api/payment/callback", (req, res) => {
  try {
    const callback = req.body?.Body?.stkCallback;

    if (
      !callback ||
      typeof callback.CheckoutRequestID !== "string" ||
      callback.ResultCode === undefined ||
      callback.ResultCode === null
    ) {
      return res.status(400).json({
        ResultCode: 1,
        ResultDesc: "Invalid callback payload."
      });
    }

    const checkoutId = callback.CheckoutRequestID;
    const payment = payments.get(checkoutId);

    console.log("Callback received:", JSON.stringify({
      checkoutRequestId: checkoutId,
      resultCode: callback.ResultCode,
      resultDescription: callback.ResultDesc
    }));

    // A callback for an unknown payment must never create a payment record.
    if (!payment) {
      console.warn("Callback for unknown checkout ID:", checkoutId);

      return res.json({
        ResultCode: 0,
        ResultDesc: "Callback acknowledged."
      });
    }

    // Do not overwrite an already processed callback.
    if (payment.callbackReceived) {
      return res.json({
        ResultCode: 0,
        ResultDesc: "Callback already processed."
      });
    }

    const metadata = metadataObject(
      callback.CallbackMetadata?.Item
    );

    payment.callbackReceived = true;
    payment.callbackResultCode = callback.ResultCode;
    payment.callbackDescription = String(
      callback.ResultDesc || ""
    ).slice(0, 300);

    if (String(callback.ResultCode) !== "0") {
      payment.status = "FAILED";
      payments.set(checkoutId, payment);

      return res.json({
        ResultCode: 0,
        ResultDesc: "Callback processed."
      });
    }

    const paidAmount = Number(metadata.Amount);
    const paidPhone = String(metadata.PhoneNumber || "");
    const receipt = String(metadata.MpesaReceiptNumber || "");

    // Require transaction details to match the initiated payment.
    if (
      !Number.isFinite(paidAmount) ||
      paidAmount !== payment.amount ||
      paidPhone !== payment.phone ||
      !receipt
    ) {
      payment.status = "REVIEW_REQUIRED";
      payment.validationError =
        "Callback payment details did not match the request.";

      payments.set(checkoutId, payment);

      console.error(
        "Payment requires manual review:",
        checkoutId
      );

      return res.json({
        ResultCode: 0,
        ResultDesc: "Callback acknowledged for review."
      });
    }

    payment.status = "SUCCESS";
    payment.amount = paidAmount;
    payment.receiptNumber = receipt;
    payment.paidAt = new Date().toISOString();

    payments.set(checkoutId, payment);

    console.log("Payment confirmed:", JSON.stringify({
      checkoutRequestId: checkoutId,
      receiptNumber: receipt,
      amount: paidAmount
    }));

    return res.json({
      ResultCode: 0,
      ResultDesc: "Callback processed."
    });
  } catch (error) {
    console.error("Callback processing error:", error.message);

    return res.status(500).json({
      ResultCode: 1,
      ResultDesc: "Callback processing failed."
    });
  }
});

// Check payment status.
app.get(
  "/api/mpesa/status/:id",
  statusLimiter,
  async (req, res) => {
    try {
      const checkoutId = req.params.id;
      const payment = payments.get(checkoutId);

      if (!payment) {
        return res.status(404).json({
          success: false,
          status: "NOT_FOUND",
          message: "Payment was not found in this server session."
        });
      }

      // A callback takes priority over a status query.
      if (payment.callbackReceived) {
        return res.json({
          success: true,
          checkoutRequestId: checkoutId,
          status: payment.status,
          amount: payment.amount,
          receiptNumber: payment.receiptNumber || null
        });
      }

      const credentials = config();
      const token = await getToken();
      const timestamp = getTimestamp();

      const result = await darajaPost(
        "/mpesa/stkpushquery/v1/query",
        {
          BusinessShortCode: credentials.shortcode,
          Password: getPassword(credentials, timestamp),
          Timestamp: timestamp,
          CheckoutRequestID: checkoutId
        },
        token
      );

      console.log("Status query:", JSON.stringify({
        checkoutRequestId: checkoutId,
        responseCode: result.ResponseCode,
        resultCode: result.ResultCode,
        resultDescription: result.ResultDesc
      }));

      // ResponseCode alone does not establish successful payment.
      if (
        result.ResultCode !== undefined &&
        result.ResultCode !== null
      ) {
        if (String(result.ResultCode) === "0") {
          // The query response alone is insufficient here to validate
          // the receipt, amount and phone. Await a verified callback.
          payment.status = "PENDING_CONFIRMATION";
        } else {
          const description = String(
            result.ResultDesc || ""
          ).toLowerCase();

          // Do not mark pending or inconclusive queries as failed.
          if (
            description.includes("processed successfully")
          ) {
            payment.status = "PENDING_CONFIRMATION";
          } else {
            payment.status = "FAILED";
          }
        }

        payments.set(checkoutId, payment);
      }

      return res.json({
        success: true,
        checkoutRequestId: checkoutId,
        status: payment.status,
        amount: payment.amount,
        message:
          payment.status === "PENDING_CONFIRMATION"
            ? "Awaiting verified payment callback."
            : "Payment status retrieved."
      });
    } catch (error) {
      console.error("Status check error:", error.message);

      const timeout =
        error.name === "TimeoutError" ||
        error.name === "AbortError";

      return res.status(timeout ? 504 : 502).json({
        success: false,
        message: safeMessage(error)
      });
    }
  }
);

// Handle malformed JSON.
app.use((error, req, res, next) => {
  if (error instanceof SyntaxError && error.status === 400) {
    return res.status(400).json({
      success: false,
      message: "Invalid JSON request body."
    });
  }

  return next(error);
});

// Unknown routes.
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: "Route not found.",
    path: req.path
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(
    "E-commerce M-PESA backend listening on port " + PORT
  );

  console.log(
    "Daraja environment:",
    (process.env.DARAJA_ENV || "sandbox").toLowerCase()
  );
});
