import * as functionsV1 from "firebase-functions/v1";
import { FieldValue } from "firebase-admin/firestore";
import { db, USERS_COLLECTION } from "../lib/firebaseAdmin";
import { REGION } from "../lib/config";

// Seeds users/{uid} with server-controlled defaults on first sign-in, before any client
// read/write attempt can race it. Both balances start at 0 - the client never gets to choose
// its own starting balance; they only grow via a verified purchase (verifyPurchase.ts).
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
        creditSecondsRemaining: 0,
        textSecondsRemaining: 0,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
  });
