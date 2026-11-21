const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validCreatePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    title: "Quarterly_Report.pdf",
    documentType: "PDF",
    sizeBytes: 24576,
    contentHash: "a1b2c3d4e5f6a1b2c3d4e5f6",
    storagePath: `users/${uid}/pdfs/doc_a1b2c3d4e5f6.pdf`,
    syncState: "SYNCED",
    localPath: "/storage/emulated/0/Download/Quarterly_Report.pdf",
    folderName: "Download",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user cannot read, list, create, update, or delete cloud_documents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const docRef = unauthDb.collection("users").doc(ALICE_UID).collection("cloud_documents").doc("doc_1");

  await assertFails(docRef.get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cloud_documents").get());
  await assertFails(docRef.set(validCreatePayload(ALICE_UID)));
  await assertFails(docRef.delete());
});

test("Owner can create, list, partial update, and delete cloud_documents matching Kotlin schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_documents");
  const docRef = colRef.doc("doc_a1b2c3d4e5f6");

  await assertSucceeds(docRef.set(validCreatePayload(ALICE_UID)));

  // Matches Kotlin query: db.collection("users").document(uid).collection("cloud_documents")
  await assertSucceeds(colRef.get());
  await assertSucceeds(docRef.get());

  // Partial update preserves immutable userId & createdAt
  await assertSucceeds(
    docRef.update({
      syncState: "METADATA_ONLY",
      title: "Quarterly_Report_Renamed.pdf",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(docRef.delete());
});

test("Rejects @DocumentId 'id' field injection, ghost fields, future timestamps, and immutable field changes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_documents");

  // Injecting @DocumentId 'id' fails hasOnly()
  await assertFails(
    colRef.doc("doc_bad_id").set(validCreatePayload(ALICE_UID, { id: "doc_bad_id" }))
  );

  // Shadow update / ghost field fails
  await assertFails(
    colRef.doc("doc_ghost").set(validCreatePayload(ALICE_UID, { isAdmin: true }))
  );

  // Invalid docId format fails isValidId()
  await assertFails(
    colRef.doc("bad.id$with_chars").set(validCreatePayload(ALICE_UID))
  );

  // Future timestamp fails <= request.time
  await assertFails(
    colRef.doc("doc_future").set(
      validCreatePayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Valid create, then reject immutable createdAt or userId tampering on update
  await assertSucceeds(colRef.doc("doc_immutable").set(validCreatePayload(ALICE_UID)));
  await assertFails(
    colRef.doc("doc_immutable").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    colRef.doc("doc_immutable").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's cloud_documents", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("cloud_documents")
      .doc("doc_alice_1")
      .set(validCreatePayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceCol = bobDb.collection("users").doc(ALICE_UID).collection("cloud_documents");

  await assertFails(aliceCol.get());
  await assertFails(aliceCol.doc("doc_alice_1").get());
  await assertFails(aliceCol.doc("doc_bob_spoof").set(validCreatePayload(ALICE_UID)));
  await assertFails(aliceCol.doc("doc_bob_spoof2").set(validCreatePayload(BOB_UID)));
  await assertFails(aliceCol.doc("doc_alice_1").update({ title: "Hacked.pdf", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(aliceCol.doc("doc_alice_1").delete());
});

test("app_config/latest_release allows get for update checks but denies list and all client writes", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("app_config")
      .doc("latest_release")
      .set({
        latestVersionCode: 2,
        latestVersionName: "1.1",
        apkUrl: "https://example.com/narayani-pdf-v1.1.apk",
        releaseNotes: "Zoom & update improvements",
        forceUpdate: false,
      });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(unauthDb.collection("app_config").doc("latest_release").get());
  await assertSucceeds(aliceDb.collection("app_config").doc("latest_release").get());
  await assertFails(unauthDb.collection("app_config").get());
  await assertFails(aliceDb.collection("app_config").get());
  await assertFails(
    aliceDb.collection("app_config").doc("latest_release").set({
      latestVersionCode: 99,
      latestVersionName: "9.9",
      apkUrl: "https://evil.example/app.apk",
    })
  );
  await assertFails(aliceDb.collection("app_config").doc("latest_release").update({ latestVersionCode: 99 }));
  await assertFails(aliceDb.collection("app_config").doc("latest_release").delete());
});
