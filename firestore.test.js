const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const GUARD_UID = "guard_alfha_01";
const OTHER_UID = "intruder_99";

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

test("Unauthenticated user: cannot read checkpoints or reports", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("condominiums").doc("PRADOS_1").collection("patrol_checkpoints").get());
  await assertFails(unauthDb.collection("condominiums").doc("PRADOS_1").collection("patrol_reports").get());
});

test("Authenticated guard: can read and write patrol checkpoint", async () => {
  const guardDb = testEnv.authenticatedContext(GUARD_UID).firestore();
  await assertSucceeds(
    guardDb.collection("condominiums").doc("PRADOS_1").collection("patrol_checkpoints").doc("CP_01").set({
      id: "CP_01",
      code: "CP-01",
      name: "Caseta Principal Acceso Norte",
      latitude: 19.432608,
      longitude: -99.133209,
      toleranceMeters: 15.0,
      isValidated: true,
      validatedAtMillis: Date.now(),
      userId: GUARD_UID
    })
  );
});

test("Authenticated guard: can create and read patrol report", async () => {
  const guardDb = testEnv.authenticatedContext(GUARD_UID).firestore();
  await assertSucceeds(
    guardDb.collection("condominiums").doc("PRADOS_1").collection("patrol_reports").doc("ROND_123").set({
      id: "ROND_123",
      timestampMillis: Date.now(),
      zone: "Sector Norte y Amenidades",
      reportText: "Rondín sin novedades. Iluminación perimetral operando al 100%.",
      isAiAssisted: true,
      userId: GUARD_UID
    })
  );
  await assertSucceeds(guardDb.collection("condominiums").doc("PRADOS_1").collection("patrol_reports").get());
});

test("Authenticated user: cannot write invalid checkpoint (missing required fields)", async () => {
  const guardDb = testEnv.authenticatedContext(GUARD_UID).firestore();
  await assertFails(
    guardDb.collection("condominiums").doc("PRADOS_1").collection("patrol_checkpoints").doc("CP_BAD").set({
      id: "CP_BAD"
      // Missing code, name, latitude, longitude, isValidated
    })
  );
});

test("User profile: user can manage their own profile, cannot modify other's", async () => {
  const guardDb = testEnv.authenticatedContext(GUARD_UID).firestore();
  await assertSucceeds(
    guardDb.collection("users").doc(GUARD_UID).set({
      id: GUARD_UID,
      email: "oficial@medusaalfha.com",
      displayName: "Oficial Ramírez",
      role: "GUARD"
    })
  );

  const intruderDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  await assertFails(
    intruderDb.collection("users").doc(GUARD_UID).set({
      id: GUARD_UID,
      email: "hacked@bad.com",
      role: "ADMIN"
    })
  );
});
