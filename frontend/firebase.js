// firebase.js
// Project: cardat-1d7d3
// Database: https://cardat-1d7d3-default-rtdb.asia-southeast1.firebasedatabase.app/

import { initializeApp } from "firebase/app";
import { getDatabase, ref, onValue, set, get } from "firebase/database";

// ── FIREBASE CONFIG (your real credentials) ──
const firebaseConfig = {
  apiKey:            "AIzaSyAan6Ebd0DQDt6t5ecpfAhezGY4KlZeadY",
  authDomain:        "cardat-1d7d3.firebaseapp.com",
  databaseURL:       "https://cardat-1d7d3-default-rtdb.asia-southeast1.firebasedatabase.app/",
  projectId:         "cardat-1d7d3",
  storageBucket:     "cardat-1d7d3.appspot.com",
  messagingSenderId: "978971602171",
  appId:             "1:978971602171:web:c6af614534d3b81e0ed45e"
};

// ── INITIALIZE ──
const app = initializeApp(firebaseConfig);
const db  = getDatabase(app);

// ─────────────────────────────────────────────────────────────
//  MAIN LISTENER — reads entire vehicleData node at once
//  Fires automatically every time Android app writes (every 1s)
// ─────────────────────────────────────────────────────────────
export function listenToVehicleData(callback) {
  const vehicleRef = ref(db, "vehicleData");

  onValue(
    vehicleRef,
    (snapshot) => {
      const raw = snapshot.val();

      // Database is empty or Android app hasn't sent data yet
      if (!raw) {
        console.warn("⚠️ vehicleData is empty — is the Android app running?");
        return;
      }

      // Map raw Firebase fields → clean object your script.js can use
      const data = {

        // vehicleData/accelerometer/x, y, z
        accelerometer: {
          x: raw.accelerometer?.x ?? 0,
          y: raw.accelerometer?.y ?? 0,
          z: raw.accelerometer?.z ?? 0,
        },

        // vehicleData/gyroscope/x, y, z
        gyroscope: {
          x: raw.gyroscope?.x ?? 0,
          y: raw.gyroscope?.y ?? 0,
          z: raw.gyroscope?.z ?? 0,
        },

        // vehicleData/crash_detection/accident_detected + g_force
        crash: {
          detected: raw.crash_detection?.accident_detected ?? false,
          gForce:   raw.crash_detection?.g_force           ?? 0,
        },

        // vehicleData/location/latitude + longitude
        location: {
          lat: raw.location?.latitude  ?? 0,
          lng: raw.location?.longitude ?? 0,
        },

        // vehicleData/emergency_alert/active + countdown
        emergency: {
          active:    raw.emergency_alert?.active    ?? false,
          countdown: raw.emergency_alert?.countdown ?? 0,
        },

        // vehicleData/user_vitals
        userVitals: {
          bloodPressure: raw.user_vitals?.blood_pressure ?? null,
          bloodType:     raw.user_vitals?.blood_type     ?? 'Unknown',
          heartRate:     raw.user_vitals?.heart_rate     ?? null,
          watchRssi:     raw.user_vitals?.watch_rssi     ?? null,
        },
      };

      console.log("🔥 Live data received:", data); // remove this line after testing
      callback(data);
    },
    (error) => {
      // Fires when Firebase Rules block the read
      console.error("❌ Firebase read failed:", error.message);
      console.error("Fix → Firebase Console → Realtime Database → Rules → set .read: true");
    }
  );
}

function normalizeEmailKey(email) {
  return String(email).toLowerCase().replace(/[.#$[\]\/]/g, "_");
}

export async function saveUserProfile(email, profile) {
  const key = normalizeEmailKey(email);
  await set(ref(db, `users/${key}`), profile);
  return profile;
}

export async function getUserProfileByEmail(email) {
  const key = normalizeEmailKey(email);
  const snapshot = await get(ref(db, `users/${key}`));
  return snapshot.exists() ? snapshot.val() : null;
}

export async function verifyUserCredentials(email, password) {
  const profile = await getUserProfileByEmail(email);
  if (!profile || profile.password !== password) return null;
  return profile;
}

export async function updatePassword(email, newPassword) {
  const profile = await getUserProfileByEmail(email);
  if (!profile) throw new Error("No account found for this email.");
  profile.password = newPassword;
  return saveUserProfile(email, profile);
}

// ─────────────────────────────────────────────────────────────
//  INDIVIDUAL LISTENERS — use these if you only need one field
// ─────────────────────────────────────────────────────────────

export function listenToAccelerometer(callback) {
  onValue(ref(db, "vehicleData/accelerometer"), (snap) => {
    const d = snap.val();
    if (d) callback({ x: d.x ?? 0, y: d.y ?? 0, z: d.z ?? 0 });
  });
}

export function listenToGyroscope(callback) {
  onValue(ref(db, "vehicleData/gyroscope"), (snap) => {
    const d = snap.val();
    if (d) callback({ x: d.x ?? 0, y: d.y ?? 0, z: d.z ?? 0 });
  });
}

export function listenToCrash(callback) {
  onValue(ref(db, "vehicleData/crash_detection"), (snap) => {
    const d = snap.val();
    if (d) callback({
      detected: d.accident_detected ?? false,
      gForce:   d.g_force           ?? 0,
    });
  });
}

export function listenToLocation(callback) {
  onValue(ref(db, "vehicleData/location"), (snap) => {
    const d = snap.val();
    if (d) callback({ lat: d.latitude ?? 0, lng: d.longitude ?? 0 });
  });
}

export function listenToEmergency(callback) {
  onValue(ref(db, "vehicleData/emergency_alert"), (snap) => {
    const d = snap.val();
    if (d) callback({
      active:    d.active    ?? false,
      countdown: d.countdown ?? 0,
    });
  });
}