import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db, PURCHASE_TOKENS_COLLECTION } from "../lib/firebaseAdmin";
import { fetchProductPurchase } from "../lib/androidPublisher";
import { grantCredit } from "../lib/usage";
import { REGION, CREDIT_PRODUCT_ID, CREDIT_SECONDS, TEXT_CREDIT_SECONDS } from "../lib/config";

interface VerifyPurchaseRequest {
  purchaseToken: string;
  productId: string;
}

// Called once by the client right after Play Billing hands back a successful Purchase of the
// one credit product (£8.99/30min, consumable - the client is responsible for calling
// consumeAsync afterward so it can be bought again). This is the actual source of truth - the
// client's purchase result is provisional until this confirms it against the Android
// Publisher API directly.
export const verifyPurchase = onCall<VerifyPurchaseRequest>(
  { region: REGION },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const uid = request.auth.uid;
    const { purchaseToken, productId } = request.data ?? {};
    if (!purchaseToken || !productId) {
      throw new HttpsError("invalid-argument", "purchaseToken and productId are required");
    }
    if (productId !== CREDIT_PRODUCT_ID) {
      throw new HttpsError("invalid-argument", `Unknown productId: ${productId}`);
    }

    let info;
    try {
      info = await fetchProductPurchase(purchaseToken, productId);
    } catch (e) {
      throw new HttpsError("internal", `Could not verify purchase: ${(e as Error).message}`);
    }

    if (info.purchaseState !== 0) {
      throw new HttpsError("failed-precondition", `Purchase is not in a purchased state (${info.purchaseState})`);
    }

    // Idempotency guard - grantCredit uses FieldValue.increment, which is NOT idempotent on
    // its own. Every purchase (including each repeat buy) gets a fresh purchaseToken from
    // Play, so a doc keyed by token created exactly once is enough to make sure a client
    // retry of this call (e.g. after a dropped response, before it heard "success") can never
    // grant the same purchase's credit twice.
    const tokenRef = db.collection(PURCHASE_TOKENS_COLLECTION).doc(purchaseToken);
    try {
      await tokenRef.create({
        uid,
        productId,
        creditSeconds: CREDIT_SECONDS,
        textSeconds: TEXT_CREDIT_SECONDS,
        createdAt: FieldValue.serverTimestamp(),
      });
    } catch (e) {
      // ALREADY_EXISTS - this exact purchase was already processed, don't grant again.
      return { grantedSeconds: 0, grantedTextSeconds: 0, alreadyProcessed: true };
    }

    await grantCredit(uid, CREDIT_SECONDS, TEXT_CREDIT_SECONDS);

    return { grantedSeconds: CREDIT_SECONDS, grantedTextSeconds: TEXT_CREDIT_SECONDS };
  }
);
