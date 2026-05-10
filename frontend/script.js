/* CraDet Monitoring Dashboard
   script.new.js | Frontend monitoring logic
   ===================================== */

import { listenToVehicleData, saveUserProfile, getUserProfileByEmail, verifyUserCredentials, updatePassword } from "./firebase.js";

const DEMO_PATIENT = {
  fullName: 'Aarav Sharma',
  age: 34,
  condition: 'Normal',
};

let currentLocationData = null;
let lastActiveLocationData = null;
let isAccidentMode = false;
let simulationTimer = null;
let countdownValue = 20;
let lastCrashDetected = false;
let lastEmergencyActive = false;

listenToVehicleData((data) => {
  console.log('Firebase live payload:', data);
  handleVehicleData(data);
});

const signupForm = document.getElementById('signupForm');
if (signupForm) signupForm.addEventListener('submit', handleSignup);

const loginForm = document.getElementById('loginForm');
if (loginForm) loginForm.addEventListener('submit', handleLogin);

const forgotForm = document.getElementById('forgotForm');
if (forgotForm) forgotForm.addEventListener('submit', handleForgotPassword);

let heartbeatChart = null;
let spo2Chart = null;
let stressChart = null;
let bpChart = null;
let movementChart = null;
let impactChart = null;

const chartLabels = Array.from({ length: 12 }, (_, index) => `${(index - 11) * 2}m`);
const heartbeatHistory = Array(12).fill(72);
const spo2History = Array(12).fill(97);
const stressHistory = Array(12).fill(18);
const bpSystolicHistory = Array(12).fill(118);
const bpDiastolicHistory = Array(12).fill(78);
const movementHistory = Array(12).fill(0.2);
const impactHistory = Array(12).fill(0.1);



function formatLocation(location) {
  if (!location) return '--';
  const latitude = location.latitude ?? location.lat;
  const longitude = location.longitude ?? location.lng;
  if (latitude == null || longitude == null) return '--';
  return `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`;
}

function getCurrentTime() {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true });
}

function showAuthMessage(message, isError = true) {
  const authMessage = document.getElementById('authMessage');
  if (!authMessage) return;
  authMessage.textContent = message;
  authMessage.style.color = isError ? '#b91c1c' : '#16a34a';
}

async function handleSignup(event) {
  event.preventDefault();
  const name = document.getElementById('signupName')?.value.trim();
  const email = document.getElementById('signupEmail')?.value.trim();
  const phone = document.getElementById('signupPhone')?.value.trim();
  const password = document.getElementById('signupPassword')?.value;

  if (!name || !email || !phone || !password) {
    showAuthMessage('Please complete all signup fields.');
    return;
  }

  try {
    await saveUserProfile(email, {
      name,
      email,
      phone,
      password,
      createdAt: new Date().toISOString(),
    });
    localStorage.setItem('craDetUserEmail', email);
    showAuthMessage('Account created. Redirecting to login...', false);
    setTimeout(() => window.location.href = 'login.html', 1400);
  } catch (error) {
    showAuthMessage(error.message || 'Unable to create account.');
  }
}

async function handleLogin(event) {
  event.preventDefault();
  const email = document.getElementById('loginEmail')?.value.trim();
  const password = document.getElementById('loginPassword')?.value;

  if (!email || !password) {
    showAuthMessage('Please enter both email and password.');
    return;
  }

  try {
    const profile = await verifyUserCredentials(email, password);
    if (!profile) {
      showAuthMessage('Invalid email or password.');
      return;
    }
    localStorage.setItem('craDetUserEmail', email);
    console.log('Logged in profile:', profile);
    showAuthMessage('Login successful. Redirecting...', false);
    setTimeout(() => window.location.href = 'index.html', 1200);
  } catch (error) {
    showAuthMessage(error.message || 'Login failed.');
  }
}

async function handleForgotPassword(event) {
  event.preventDefault();
  const email = document.getElementById('forgotEmail')?.value.trim();
  const password = document.getElementById('forgotPassword')?.value;
  const confirmPassword = document.getElementById('forgotConfirmPassword')?.value;

  if (!email || !password || !confirmPassword) {
    showAuthMessage('Please fill out all password reset fields.');
    return;
  }

  if (password !== confirmPassword) {
    showAuthMessage('Passwords do not match.');
    return;
  }

  try {
    await updatePassword(email, password);
    showAuthMessage('Password updated. Redirecting to login...', false);
    setTimeout(() => window.location.href = 'login.html', 1400);
  } catch (error) {
    showAuthMessage(error.message || 'Unable to reset password.');
  }
}

function updateSensorWidgets(data) {
  const ax = data.accelerometer?.x ?? 0;
  const ay = data.accelerometer?.y ?? 0;
  const az = data.accelerometer?.z ?? 0;
  const gx = data.gyroscope?.x ?? 0;
  const gy = data.gyroscope?.y ?? 0;
  const gz = data.gyroscope?.z ?? 0;
  const accelMagnitude = Math.sqrt(ax * ax + ay * ay + az * az);
  const gyroMagnitude = Math.sqrt(gx * gx + gy * gy + gz * gz);
  const crashGForce = data.crash?.gForce ?? 0;

  const accelValue = document.getElementById('accelerometerMagnitude');
  const gyroValue = document.getElementById('gyroscopeMagnitude');
  const crashValue = document.getElementById('crashGForce');
  const accelBar = document.getElementById('accelerometerBar');
  const gyroBar = document.getElementById('gyroscopeBar');
  const crashBar = document.getElementById('crashForceBar');

  if (accelValue) accelValue.textContent = accelMagnitude.toFixed(2);
  if (gyroValue) gyroValue.textContent = gyroMagnitude.toFixed(2);
  if (crashValue) crashValue.textContent = crashGForce.toFixed(2);

  if (accelBar) accelBar.style.width = `${Math.min(accelMagnitude / 12, 1) * 100}%`;
  if (gyroBar) gyroBar.style.width = `${Math.min(gyroMagnitude / 10, 1) * 100}%`;
  if (crashBar) crashBar.style.width = `${Math.min(crashGForce / 10, 1) * 100}%`;

  const movementCard = document.getElementById('movementIntensity');
  const movementBar = document.getElementById('movementBar');
  if (movementCard) movementCard.textContent = accelMagnitude.toFixed(2);
  if (movementBar) movementBar.style.width = `${Math.min(accelMagnitude / 12, 1) * 100}%`;

  if (data.userVitals) {
    const heartRateValue = document.getElementById('heartRate');
    const patientBloodType = document.getElementById('patientBloodType');
    const watchRssiValue = document.getElementById('watchRssi');
    if (heartRateValue && typeof data.userVitals.heartRate === 'number') {
      heartRateValue.textContent = data.userVitals.heartRate;
    }
    if (patientBloodType && data.userVitals.bloodType) {
      patientBloodType.textContent = data.userVitals.bloodType;
    }
    if (watchRssiValue && data.userVitals.watchRssi != null) {
      watchRssiValue.textContent = `${data.userVitals.watchRssi} dBm`;
    }
  }
}

function updateRealtimeCharts(data) {
  const accelMagnitude = Math.sqrt(
    Math.pow(data.accelerometer?.x ?? 0, 2) +
    Math.pow(data.accelerometer?.y ?? 0, 2) +
    Math.pow(data.accelerometer?.z ?? 0, 2)
  );
  const crashGForce = data.crash?.gForce ?? 0;

  movementHistory.push(accelMagnitude);
  impactHistory.push(crashGForce);

  if (movementHistory.length > 12) movementHistory.shift();
  if (impactHistory.length > 12) impactHistory.shift();

  if (movementChart) movementChart.update('none');
  if (impactChart) impactChart.update('none');
}

function loadStoredUserProfile() {
  const email = localStorage.getItem('craDetUserEmail');
  if (!email) return;

  getUserProfileByEmail(email).then((profile) => {
    if (!profile) return;
    console.log('Loaded stored user profile:', profile);
    const patientName = document.getElementById('patientName');
    const patientEmail = document.getElementById('patientEmail');
    const patientPhone = document.getElementById('patientPhone');
    if (patientName && profile.name) {
      patientName.textContent = profile.name;
    }
    if (patientEmail && profile.email) {
      patientEmail.textContent = profile.email;
    }
    if (patientPhone && profile.phone) {
      patientPhone.textContent = profile.phone;
    }
  }).catch((error) => {
    console.error('Unable to load stored user profile:', error);
  });
}

function handleVehicleData(data) {
  if (!data) return;

  if (data.location?.lat != null && data.location?.lng != null) {
    if (currentLocationData) {
      lastActiveLocationData = currentLocationData;
    }
    currentLocationData = {
      latitude: data.location.lat,
      longitude: data.location.lng,
      lat: data.location.lat,
      lng: data.location.lng,
    };
    renderLocationCards();
  }

  if (typeof data.emergency?.countdown === 'number') {
    countdownValue = data.emergency.countdown;
    updateAlertCountdown();
  }

  const crashDetected = Boolean(data.crash?.detected);
  const emergencyActive = Boolean(data.emergency?.active);

  if (crashDetected && !lastCrashDetected) {
    isAccidentMode = true;
    updateDashboardSummary('Incident Detected', 'High', emergencyActive ? 'Emergency active' : 'Crash detected');
    addAlertLog('Crash Detected', `G-force: ${data.crash.gForce ?? 0}`, 'critical');
  } else if (!crashDetected && emergencyActive && !lastEmergencyActive) {
    updateDashboardSummary('Emergency Alert', 'Elevated', `Countdown ${countdownValue}s`);
    addAlertLog('Emergency Alert', `Countdown: ${countdownValue}s`, 'info');
  } else if (!crashDetected && !emergencyActive && !isAccidentMode) {
    updateDashboardSummary('Monitoring', 'Low', 'Awaiting event');
  }

  if (!isAccidentMode && emergencyActive) {
    updateDashboardSummary('Emergency Alert', 'Elevated', `Countdown ${countdownValue}s`);
  }

  updateSensorWidgets(data);
  updateRealtimeCharts(data);

  if (data.userVitals?.bloodPressure) {
    const [systolic, diastolic] = data.userVitals.bloodPressure.split('/').map(Number);
    if (!Number.isNaN(systolic) && !Number.isNaN(diastolic)) {
      bpSystolicHistory.push(systolic);
      bpDiastolicHistory.push(diastolic);
      if (bpSystolicHistory.length > 12) bpSystolicHistory.shift();
      if (bpDiastolicHistory.length > 12) bpDiastolicHistory.shift();
      if (bpChart) bpChart.update('none');
    }
  }

  lastCrashDetected = crashDetected;
  lastEmergencyActive = emergencyActive;

  document.getElementById('lastUpdated').textContent = getCurrentTime();
}

function renderLocationCards() {
  const current = document.getElementById('currentLocation');
  const last = document.getElementById('lastLocation');
  const coords = document.getElementById('mapCoordinates');
  const mapsLink = document.getElementById('mapsLink');
  const locationStatus = document.getElementById('locationStatus');

  if (current) {
    current.textContent = currentLocationData ? formatLocation(currentLocationData) : 'Waiting for location...';
  }
  if (last) {
    last.textContent = lastActiveLocationData ? formatLocation(lastActiveLocationData) : 'Not recorded';
  }
  if (coords) {
    coords.textContent = currentLocationData ? formatLocation(currentLocationData) : '--';
  }
  if (mapsLink) {
    if (currentLocationData) {
      mapsLink.href = `https://maps.google.com/?q=${currentLocationData.latitude},${currentLocationData.longitude}`;
      mapsLink.target = '_blank';
      locationStatus.textContent = 'Active';
    } else {
      mapsLink.href = '#';
      if (locationStatus) locationStatus.textContent = 'Unavailable';
    }
  }
}

function fetchCurrentLocation() {
  if (!navigator.geolocation) {
    const locationStatus = document.getElementById('locationStatus');
    if (locationStatus) locationStatus.textContent = 'Unsupported';
    return;
  }

  navigator.geolocation.getCurrentPosition(
    (position) => {
      const { latitude, longitude } = position.coords;
      if (currentLocationData) {
        lastActiveLocationData = currentLocationData;
      }
      currentLocationData = { latitude, longitude };
      renderLocationCards();
    },
    () => {
      const locationStatus = document.getElementById('locationStatus');
      if (locationStatus) locationStatus.textContent = 'Permission denied';
    },
    { enableHighAccuracy: true, timeout: 10000 }
  );
}

function createChart(ctx, label, borderColor, backgroundColor, initialData, extraDatasets = []) {
  const datasets = [
    {
      label,
      data: initialData,
      borderColor,
      backgroundColor,
      tension: 0.35,
      borderWidth: 3,
      fill: true,
      pointRadius: 0,
    },
    ...extraDatasets,
  ];

  return new Chart(ctx, {
    type: 'line',
    data: {
      labels: [...chartLabels],
      datasets,
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: extraDatasets.length > 0,
          labels: { color: '#475569', font: { size: 12 } },
        },
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { color: '#64748b' },
        },
        y: {
          grid: { color: '#e2e8f0' },
          ticks: { color: '#64748b' },
        },
      },
    },
  });
}

function initCharts() {
  heartbeatChart = createChart(
    document.getElementById('heartbeatChart').getContext('2d'),
    'BPM',
    '#ef4444',
    'rgba(239, 68, 68, 0.18)',
    heartbeatHistory
  );

  spo2Chart = createChart(
    document.getElementById('spo2Chart').getContext('2d'),
    'SpO₂ %',
    '#2563eb',
    'rgba(37, 99, 235, 0.18)',
    spo2History
  );

  stressChart = createChart(
    document.getElementById('stressChart').getContext('2d'),
    'Stress',
    '#f97316',
    'rgba(249, 115, 22, 0.18)',
    stressHistory
  );

  bpChart = createChart(
    document.getElementById('bpChart').getContext('2d'),
    'Systolic',
    '#0f172a',
    'rgba(15, 23, 42, 0.15)',
    bpSystolicHistory,
    [
      {
        label: 'Diastolic',
        data: bpDiastolicHistory,
        borderColor: '#22c55e',
        backgroundColor: 'rgba(34, 197, 94, 0.18)',
        tension: 0.35,
        borderWidth: 3,
        fill: true,
        pointRadius: 0,
      },
    ]
  );

  movementChart = createChart(
    document.getElementById('movementChart').getContext('2d'),
    'Movement',
    '#0284c7',
    'rgba(2, 132, 199, 0.18)',
    movementHistory
  );

  impactChart = createChart(
    document.getElementById('impactChart').getContext('2d'),
    'Impact',
    '#8b5cf6',
    'rgba(139, 92, 246, 0.18)',
    impactHistory
  );
}

function updateDashboardSummary(status = 'Monitoring', severity = 'Low', emergency = 'Awaiting event') {
  const severityLabel = document.getElementById('alertSeverity');
  document.getElementById('alertStatusText').textContent = status;
  document.getElementById('alertSeverityText').textContent = severity;
  document.getElementById('emergencyStatus').textContent = emergency;
  if (severityLabel) {
    severityLabel.textContent = severity === 'High' ? 'Critical' : severity === 'Elevated' ? 'Warning' : 'No Active Alert';
  }
}

function updateHealthWidgets(data) {
  document.getElementById('heartRate').textContent = data.hr;
  document.getElementById('spo2').textContent = data.spo2;
  document.getElementById('stressLevel').textContent = data.stressLabel;
  document.getElementById('bodyTemp').textContent = data.temp.toFixed(1);
  document.getElementById('breathingRate').textContent = data.breathing;
  document.getElementById('movementIntensity').textContent = data.movement.toFixed(1);

  document.getElementById('heartBar').style.width = `${Math.min(data.hr / 140, 1) * 100}%`;
  document.getElementById('spo2Bar').style.width = `${Math.min(data.spo2 / 100, 1) * 100}%`;
  document.getElementById('stressBar').style.width = `${Math.min(data.stress / 100, 1) * 100}%`;
  document.getElementById('tempBar').style.width = `${Math.min((data.temp - 96) / 6, 1) * 100}%`;
  document.getElementById('breathingBar').style.width = `${Math.min(data.breathing / 25, 1) * 100}%`;
  document.getElementById('movementBar').style.width = `${Math.min(data.movement / 3, 1) * 100}%`;
}

function addAlertLog(title, message, level = 'info') {
  const list = document.getElementById('liveAlertsList');
  if (!list) return;

  const item = document.createElement('div');
  item.className = 'alert-pill-card';
  item.style.background = level === 'critical' ? '#fee2e2' : '#e0f2fe';
  item.innerHTML = `
    <div style="display:flex; justify-content:space-between; gap:12px; align-items:center;">
      <strong style="color:${level === 'critical' ? '#b91c1c' : '#0369a1'};">${title}</strong>
      <span style="color:#475569; font-size:0.88rem;">${new Date().toLocaleTimeString([], { hour12: true })}</span>
    </div>
    <p style="margin: 10px 0 0; color:#334155; line-height:1.6;">${message}</p>
  `;

  list.prepend(item);
  if (list.children.length > 6) {
    list.removeChild(list.lastChild);
  }
}

function updateAlertCountdown() {
  const countdown = document.getElementById('alertCountdown');
  if (countdown) countdown.textContent = `${countdownValue}s`;
}



function generateHealthData(isAlert = false) {
  if (isAlert) {
    const hr = Math.floor(Math.random() * 16 + 110);
    const spo2 = Math.floor(Math.random() * 4 + 91);
    const stress = Math.floor(Math.random() * 35 + 55);
    const temp = parseFloat((Math.random() * 0.9 + 99.0).toFixed(1));
    const breathing = Math.floor(Math.random() * 6 + 20);
    const movement = parseFloat((Math.random() * 2.5 + 1.2).toFixed(1));
    return {
      hr,
      spo2,
      stress,
      stressLabel: stress > 65 ? 'High' : 'Elevated',
      temp,
      breathing,
      movement,
      bpSystolic: Math.floor(Math.random() * 18 + 130),
      bpDiastolic: Math.floor(Math.random() * 14 + 85),
      impact: parseFloat((Math.random() * 3.5 + 4.0).toFixed(1)),
    };
  }

  const hr = Math.floor(Math.random() * 10 + 68);
  const spo2 = Math.floor(Math.random() * 3 + 96);
  const stress = Math.floor(Math.random() * 20 + 12);
  const temp = parseFloat((Math.random() * 0.7 + 97.8).toFixed(1));
  const breathing = Math.floor(Math.random() * 4 + 14);
  const movement = parseFloat((Math.random() * 0.4 + 0.1).toFixed(1));
  return {
    hr,
    spo2,
    stress,
    stressLabel: stress < 25 ? 'Low' : stress < 45 ? 'Moderate' : 'Elevated',
    temp,
    breathing,
    movement,
    bpSystolic: Math.floor(Math.random() * 10 + 112),
    bpDiastolic: Math.floor(Math.random() * 8 + 72),
    impact: parseFloat((Math.random() * 0.4 + 0.05).toFixed(1)),
  };
}

function updateChartData(data) {
  heartbeatHistory.push(data.hr);
  spo2History.push(data.spo2);
  stressHistory.push(data.stress);
  bpSystolicHistory.push(data.bpSystolic);
  bpDiastolicHistory.push(data.bpDiastolic);
  movementHistory.push(data.movement);
  impactHistory.push(data.impact);

  if (heartbeatHistory.length > 12) heartbeatHistory.shift();
  if (spo2History.length > 12) spo2History.shift();
  if (stressHistory.length > 12) stressHistory.shift();
  if (bpSystolicHistory.length > 12) bpSystolicHistory.shift();
  if (bpDiastolicHistory.length > 12) bpDiastolicHistory.shift();
  if (movementHistory.length > 12) movementHistory.shift();
  if (impactHistory.length > 12) impactHistory.shift();

  heartbeatChart.update('none');
  spo2Chart.update('none');
  stressChart.update('none');
  bpChart.update('none');
  movementChart.update('none');
  impactChart.update('none');
}

function startSimulationCountdown() {
  if (simulationTimer) return;

  countdownValue = 20;
  updateAlertCountdown();
  updateDashboardSummary('Countdown active', 'Pending', 'Waiting for confirmation');

  simulationTimer = setInterval(() => {
    countdownValue -= 1;
    updateAlertCountdown();
    if (countdownValue <= 0) {
      clearInterval(simulationTimer);
      simulationTimer = null;
      triggerAccidentScenario();
    }
  }, 1000);
}

function stopSimulationCountdown() {
  if (simulationTimer) {
    clearInterval(simulationTimer);
    simulationTimer = null;
  }
  countdownValue = 20;
  updateAlertCountdown();
}

function simulateAccident() {
  if (isAccidentMode) return;
  isAccidentMode = true;
  stopSimulationCountdown();
  fetchCurrentLocation();
  addAlertLog('Simulation', 'Sample accident data generation started.', 'info');
  startSimulationCountdown();
}

function triggerAccidentScenario() {
  const data = generateHealthData(true);
  const severityLabel = data.stress > 65 ? 'High' : 'Elevated';
  const statusText = 'Incident Detected';

  document.getElementById('patientConditionDetail').textContent = 'Alert';
  document.getElementById('patientCondition').textContent = 'At Risk';
  document.getElementById('lastUpdated').textContent = getCurrentTime();
  updateDashboardSummary(statusText, severityLabel, 'Hospital notified');
  addAlertLog('Accident Alert', 'Possible accident detected. Monitoring dashboards updated.', 'critical');

  updateHealthWidgets(data);
  updateChartData(data);
}

function resetSystem() {
  isAccidentMode = false;
  stopSimulationCountdown();
  document.getElementById('patientConditionDetail').textContent = 'Normal';
  document.getElementById('patientCondition').textContent = 'Stable';
  updateDashboardSummary('Monitoring', 'Low', 'Awaiting event');
  const normalData = generateHealthData(false);
  updateHealthWidgets(normalData);
  updateChartData(normalData);
  addAlertLog('Reset', 'Dashboard has been reset to normal monitoring state.', 'info');
}

function generateHospitalData() {
  const aPlusBlood = Math.floor(Math.random() * 10 + 10); // 10-20
  const bPlusBlood = Math.floor(Math.random() * 8 + 8); // 8-16
  const oPlusBlood = Math.floor(Math.random() * 15 + 15); // 15-30
  const abPlusBlood = Math.floor(Math.random() * 5 + 3); // 3-8
  const emergencyBeds = Math.floor(Math.random() * 5 + 6); // 6-10 available out of 12
  const accidentBeds = Math.floor(Math.random() * 4 + 3); // 3-6 available out of 10
  const icuBeds = Math.floor(Math.random() * 3 + 1); // 1-3 available out of 6
  const totalBeds = emergencyBeds + accidentBeds + icuBeds;
  return { aPlusBlood, bPlusBlood, oPlusBlood, abPlusBlood, emergencyBeds, accidentBeds, icuBeds, totalBeds };
}

function updateHospitalWidgets(data) {
  const unitVolume = 450; // mL per blood unit
  document.getElementById('aPlusBlood').textContent = `${data.aPlusBlood} units (${data.aPlusBlood * unitVolume} mL)`;
  document.getElementById('bPlusBlood').textContent = `${data.bPlusBlood} units (${data.bPlusBlood * unitVolume} mL)`;
  document.getElementById('oPlusBlood').textContent = `${data.oPlusBlood} units (${data.oPlusBlood * unitVolume} mL)`;
  document.getElementById('abPlusBlood').textContent = `${data.abPlusBlood} units (${data.abPlusBlood * unitVolume} mL)`;

  // Sidebar
  document.getElementById('emergencyWardStatus').textContent = `${data.emergencyBeds} beds free`;
  document.getElementById('accidentCareStatus').textContent = `${data.accidentBeds} beds free`;
  document.getElementById('icuStatus').textContent = `${data.icuBeds} beds free`;
  document.getElementById('totalBedsStatus').textContent = `${data.totalBeds} beds`;
}

function updatePatientOverview() {
  document.getElementById('patientName').textContent = DEMO_PATIENT.fullName;
  document.getElementById('patientAge').textContent = DEMO_PATIENT.age;
  document.getElementById('patientConditionDetail').textContent = DEMO_PATIENT.condition;
  document.getElementById('lastUpdated').textContent = getCurrentTime();
}

function startLiveDataLoop() {
  setInterval(() => {
    if (!isAccidentMode) {
      const data = generateHealthData(false);
      updateHealthWidgets(data);
      updateChartData(data);
      document.getElementById('lastUpdated').textContent = getCurrentTime();
    }
    // Update hospital data periodically
    const hospitalData = generateHospitalData();
    updateHospitalWidgets(hospitalData);
  }, 3000);
}

function init() {
  initCharts();
  updatePatientOverview();
  loadStoredUserProfile();
  updateDashboardSummary();
  fetchCurrentLocation();
  const initialHospitalData = generateHospitalData();
  updateHospitalWidgets(initialHospitalData);
  startLiveDataLoop();
  setInterval(fetchCurrentLocation, 15000);
}

window.addEventListener('load', init);
