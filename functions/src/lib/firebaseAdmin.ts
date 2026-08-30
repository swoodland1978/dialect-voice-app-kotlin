import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, Firestore } from "firebase-admin/firestore";

if (getApps().length === 0) {
  initializeApp();
}

export const db: Firestore = getFirestore();

export const USERS_COLLECTION = "users";
export const PURCHASE_TOKENS_COLLECTION = "purchaseTokens";
export const USAGE_EVENTS_SUBCOLLECTION = "usageEvents";
