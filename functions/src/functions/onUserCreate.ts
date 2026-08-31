import * as functionsV1 from "firebase-functions/v1";
import { FieldValue } from "firebase-admin/firestore";
import { db, USERS_COLLECTION } from "../lib/firebaseAdmin";
import { REGION, FREE_VOICE_SECONDS, FREE_TEXT_SECONDS } from "../lib/config";

// Seeds users/{uid} with server-controlled defaults on first sign-in, before any client
// read/write attempt can race it. Balances start at the one-time free grant (config.ts) -
// the client never gets to choose its own starting balance; beyond the free grant they only
// grow via a verified purchase (verifyPurchase.ts). Runs exactly once per account.
export const onUserCreate = functionsV1
  .region(REGION)
  .auth.user()
  .onCreate(async (user) => {
    await db
      .collection(USERS_COLLECTION)
      .doc(user.uid)
      .set({
        email: user.email ?? null,
        displayName: user.displayName ?? null,
        creditSecondsRemaining: FREE_VOICE_SECONDS,
        textSecondsRemaining: FREE_TEXT_SECONDS,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
  });
