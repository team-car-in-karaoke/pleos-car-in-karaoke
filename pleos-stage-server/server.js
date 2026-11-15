const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const os = require('os');
const path = require('path');

const PORT = process.env.PORT || 3000;
const app = express();
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Allow-Private-Network', 'true');
  if (req.method === 'OPTIONS') {
    return res.sendStatus(204);
  }
  next();
});
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  },
  maxHttpBufferSize: 1e7,
  pingInterval: 5000,
  pingTimeout: 10000
});

// Active rooms indexed by 4-digit PIN
// Structure: { pin, roomId, carSocketId, micSocketIds: Set, state: {...}, createdAt }
const rooms = new Map();
let activeTunnelUrl = process.env.TUNNEL_URL || null;

function getLocalLanIp() {
  const interfaces = os.networkInterfaces();
  let fallbackIp = '127.0.0.1';
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        if (iface.address.startsWith('192.168.') || iface.address.startsWith('10.') || iface.address.startsWith('172.')) {
          return iface.address;
        }
        if (!iface.address.startsWith('169.254.')) {
          fallbackIp = iface.address;
        }
      }
    }
  }
  return fallbackIp;
}

function generatePin() {
  let pin = String(Math.floor(1000 + Math.random() * 9000));
  while (rooms.has(pin)) {
    pin = String(Math.floor(1000 + Math.random() * 9000));
  }
  return pin;
}

// REST endpoint for network discovery & active rooms
app.get('/api/info', (req, res) => {
  const lanIp = getLocalLanIp();
  const activeRooms = Array.from(rooms.values()).map(r => ({
    pin: r.pin,
    roomId: r.roomId,
    hasCar: !!r.carSocketId,
    micCount: r.micSocketIds.size
  }));
  res.json({
    status: 'ok',
    lanIp,
    port: PORT,
    lanUrl: `http://${lanIp}:${PORT}`,
    emulatorUrl: `http://10.0.2.2:${PORT}`,
    tunnelUrl: activeTunnelUrl,
    activeRooms
  });
});

app.post('/api/tunnel', (req, res) => {
  if (req.body && req.body.tunnelUrl) {
    activeTunnelUrl = req.body.tunnelUrl;
    io.emit('tunnel-updated', { tunnelUrl: activeTunnelUrl });
    console.log(`[Pleos Server] HTTPS Tunnel registered: ${activeTunnelUrl}`);
  }
  res.json({ ok: true, tunnelUrl: activeTunnelUrl });
});

io.on('connection', (socket) => {
  const clientIp = socket.handshake.address;
  console.log(`[Socket] Client connected: ${socket.id} (${clientIp})`);

  // 1. Car registers a session room with a 4-digit PIN
  socket.on('register-car', (payload = {}, callback) => {
    const requestedPin = payload.pin && /^\d{4}$/.test(String(payload.pin)) ? String(payload.pin) : null;
    let pin = requestedPin || generatePin();

    let room = rooms.get(pin);
    if (!room) {
      room = {
        pin,
        roomId: `pleos-room-${pin}`,
        carSocketId: socket.id,
        micSocketIds: new Set(),
        state: {
          currentSongId: 'kpop_neon_drive',
          isPlaying: false,
          keyShift: 0,
          echoLevel: 75,
          isParked: true
        },
        createdAt: Date.now()
      };
      rooms.set(pin, room);
    } else {
      room.carSocketId = socket.id;
    }

    socket.join(room.roomId);
    socket.data.role = 'car';
    socket.data.pin = pin;
    socket.data.roomId = room.roomId;

    const lanIp = getLocalLanIp();
    const response = {
      ok: true,
      pin: room.pin,
      roomId: room.roomId,
      lanIp,
      port: PORT,
      lanUrl: `http://${lanIp}:${PORT}`,
      tunnelUrl: activeTunnelUrl,
      micCount: room.micSocketIds.size
    };

    console.log(`[Room ${pin}] Car registered (${socket.id}). LAN URL: ${response.lanUrl}`);
    if (typeof callback === 'function') callback(response);
    socket.emit('car-registered', response);

    // Notify existing mics in this room that Car is ready
    socket.to(room.roomId).emit('car-ready', { pin: room.pin, roomId: room.roomId, state: room.state });
  });

  // 2. Smartphone Mic joins via 4-digit PIN (or auto-joins active car room if PIN is 'AUTO' or matches)
  socket.on('join-pin', (payload = {}, callback) => {
    let pin = String(payload.pin || '').trim();

    // Convenience: if only 1 room exists and PIN wasn't found, or '0000' quick-connect is used, match or create
    let room = rooms.get(pin);
    if (!room && rooms.size === 1 && pin === 'AUTO') {
      room = Array.from(rooms.values())[0];
      pin = room.pin;
    }
    if (!room && /^\d{4}$/.test(pin)) {
      // Allow pre-creating the room if the user typed the PIN printed on Car UI before Car socket finished connecting
      room = {
        pin,
        roomId: `pleos-room-${pin}`,
        carSocketId: null,
        micSocketIds: new Set(),
        state: {
          currentSongId: 'kpop_neon_drive',
          isPlaying: false,
          keyShift: 0,
          echoLevel: 75,
          isParked: true
        },
        createdAt: Date.now()
      };
      rooms.set(pin, room);
    }

    if (!room) {
      const errResp = { ok: false, error: '유효하지 않은 4자리 PIN 번호입니다.' };
      if (typeof callback === 'function') callback(errResp);
      socket.emit('join-error', errResp);
      return;
    }

    room.micSocketIds.add(socket.id);
    socket.join(room.roomId);
    socket.data.role = 'mic';
    socket.data.pin = room.pin;
    socket.data.roomId = room.roomId;
    socket.data.deviceName = payload.deviceName || 'Pleos Smart Mic';

    const okResp = {
      ok: true,
      pin: room.pin,
      roomId: room.roomId,
      carConnected: !!room.carSocketId,
      state: room.state
    };

    console.log(`[Room ${room.pin}] Smartphone Mic joined (${socket.id}, ${socket.data.deviceName})`);
    if (typeof callback === 'function') callback(okResp);
    socket.emit('paired-with-car', okResp);

    // Notify Car immediately so Car updates UI & initiates/accepts WebRTC
    io.to(room.roomId).emit('mic-connected', {
      micId: socket.id,
      deviceName: socket.data.deviceName,
      pin: room.pin,
      micCount: room.micSocketIds.size
    });
  });

  // 3. WebRTC Signaling Relay (Offer, Answer, ICE Candidate)
  socket.on('webrtc-offer', (data = {}) => {
    const roomId = socket.data.roomId || `pleos-room-${data.pin}`;
    socket.to(roomId).emit('webrtc-offer', {
      sdp: data.sdp,
      from: socket.id,
      pin: socket.data.pin || data.pin
    });
  });

  socket.on('webrtc-answer', (data = {}) => {
    const roomId = socket.data.roomId || `pleos-room-${data.pin}`;
    socket.to(roomId).emit('webrtc-answer', {
      sdp: data.sdp,
      from: socket.id,
      pin: socket.data.pin || data.pin
    });
  });

  socket.on('webrtc-ice-candidate', (data = {}) => {
    const roomId = socket.data.roomId || `pleos-room-${data.pin}`;
    socket.to(roomId).emit('webrtc-ice-candidate', {
      candidate: data.candidate,
      from: socket.id,
      pin: socket.data.pin || data.pin
    });
  });

  // 4. Real-time Vocal Telemetry (RMS 0-100 & Pitch Hz @ 60fps)
  socket.on('vocal-telemetry', (data = {}) => {
    const roomId = socket.data.roomId || `pleos-room-${data.pin}`;
    socket.volatile.to(roomId).emit('vocal-telemetry', {
      rms: data.rms || 0,
      db: data.db || -60,
      pitch: data.pitch || 0,
      note: data.note || '-',
      from: socket.id,
      ts: data.ts || Date.now()
    });
  });

  // 5. Hybrid Low-Latency PCM Audio Stream Bridge (Fallback when Emulator NAT blocks UDP)
  socket.on('audio-pcm-chunk', (data) => {
    const roomId = socket.data.roomId;
    if (roomId) {
      socket.volatile.to(roomId).emit('audio-pcm-chunk', data);
    }
  });

  // 6. Remote Control Events from Smartphone Mic -> Car
  socket.on('remote-command', (cmd = {}) => {
    const roomId = socket.data.roomId || `pleos-room-${cmd.pin}`;
    const room = rooms.get(socket.data.pin || cmd.pin);
    if (room && cmd.type) {
      if (cmd.type === 'SELECT_SONG' && cmd.songId) room.state.currentSongId = cmd.songId;
      if (cmd.type === 'START_SONG') room.state.isPlaying = true;
      if (cmd.type === 'PAUSE_SONG' || cmd.type === 'STOP_SONG') room.state.isPlaying = false;
      if (cmd.type === 'CHANGE_KEY' && typeof cmd.keyShift === 'number') room.state.keyShift = cmd.keyShift;
      if (cmd.type === 'CHANGE_ECHO' && typeof cmd.echoLevel === 'number') room.state.echoLevel = cmd.echoLevel;
    }
    console.log(`[Room ${socket.data.pin || cmd.pin}] Remote Command:`, cmd);
    io.to(roomId).emit('remote-command', {
      ...cmd,
      from: socket.id,
      ts: Date.now()
    });
  });

  // 7. Car State Updates -> Smartphone Mic
  socket.on('car-state-sync', (state = {}) => {
    const roomId = socket.data.roomId || `pleos-room-${state.pin}`;
    const room = rooms.get(socket.data.pin || state.pin);
    if (room) {
      room.state = { ...room.state, ...state };
    }
    socket.to(roomId).emit('car-state-sync', state);
  });

  socket.on('disconnect', () => {
    const { role, pin, roomId } = socket.data;
    if (pin && rooms.has(pin)) {
      const room = rooms.get(pin);
      if (role === 'mic') {
        room.micSocketIds.delete(socket.id);
        io.to(roomId).emit('mic-disconnected', {
          micId: socket.id,
          micCount: room.micSocketIds.size
        });
      } else if (role === 'car' && room.carSocketId === socket.id) {
        room.carSocketId = null;
        io.to(roomId).emit('car-disconnected', { pin });
      }
    }
    console.log(`[Socket] Disconnected: ${socket.id} (role=${role || 'unknown'}, pin=${pin || '-'})`);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  const lanIp = getLocalLanIp();
  console.log('==================================================================');
  console.log(' 🎤 PLEOS STAGE SIGNALING & WEBAUDIO SERVER RUNNING');
  console.log(` • Local / Host PC   : http://localhost:${PORT}`);
  console.log(` • AAOS Emulator URL : http://10.0.2.2:${PORT}`);
  console.log(` • Real Phone Wi-Fi  : http://${lanIp}:${PORT}`);
  console.log('==================================================================');
});
