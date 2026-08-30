import { google, androidpublisher_v3 } from "googleapis";

const PACKAGE_NAME = "com.dialect.voice";

let cachedClient: androidpublisher_v3.Androidpublisher | null = null;

// Auth comes from Application Default Credentials - the Cloud Functions runtime service
// account, which must be linked and granted access in Play Console -> Setup -> API access.
// No key file needed here.
async function getClient(): Promise<androidpublisher_v3.Androidpublisher> {
  if (cachedClient) return cachedClient;
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  cachedClient = google.androidpublisher({ version: "v3", auth });
  return cachedClient;
}

export interface ProductPurchaseInfo {
  // 0 = purchased, 1 = canceled, 2 = pending. Only 0 should ever grant credit.
  purchaseState: number;
  orderId: string | null;
}

// Verifies a one-time product purchase directly against Google's servers - the client's
// local Purchase result is provisional until this confirms it. The product is consumable, so
// fulfillment (and implicit acknowledgement) happens client-side via consumeAsync - there's
// nothing for the server to acknowledge here.
export async function fetchProductPurchase(
  purchaseToken: string,
  productId: string
): Promise<ProductPurchaseInfo> {
  const client = await getClient();
  const res = await client.purchases.products.get({
    packageName: PACKAGE_NAME,
    productId,
    token: purchaseToken,
  });

  const data = res.data;
  return {
    purchaseState: data.purchaseState ?? 1,
    orderId: data.orderId ?? null,
  };
}

export { PACKAGE_NAME };
